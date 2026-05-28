package com.finance.notification.core.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.common.security.UserStatusPort;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.email.UserEmailLookup;
import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.model.Notification;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.user.UserPreferenceCacheService;
import com.finance.notification.user.UserPreferenceCacheService.UserPreferenceSnapshot;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central entry point that turns a {@link NotificationRequest} into persisted in-app and/or email
 * outbox rows. It routes to the {@link NotificationHandler} registered for the payload type,
 * suppresses delivery for inactive users, and honours each user's per-type in-app/email preferences.
 * Single dispatches run in their own transaction; batched dispatch pages through requests and
 * bulk-loads preferences, locale/theme snapshots and active status to minimize round-trips.
 */
@Log4j2
@Service
public class NotificationDispatcher {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserPreferenceCacheService userPreferenceCacheService;
    private final UserEmailLookup userEmailLookup;
    private final UserStatusPort userStatus;
    private final ObjectMapper objectMapper;
    private final NotificationPersister persister;
    private final NotificationDispatchProperties dispatchProperties;
    private final Map<NotificationType, NotificationHandler> handlers;

    public NotificationDispatcher(NotificationPreferenceRepository preferenceRepository,
                                  UserPreferenceCacheService userPreferenceCacheService,
                                  UserEmailLookup userEmailLookup,
                                  UserStatusPort userStatus,
                                  ObjectMapper objectMapper,
                                  NotificationPersister persister,
                                  NotificationDispatchProperties dispatchProperties,
                                  List<NotificationHandler> handlerList) {
        this.preferenceRepository = preferenceRepository;
        this.userPreferenceCacheService = userPreferenceCacheService;
        this.userEmailLookup = userEmailLookup;
        this.userStatus = userStatus;
        this.objectMapper = objectMapper;
        this.persister = persister;
        this.dispatchProperties = dispatchProperties;
        this.handlers = new EnumMap<>(NotificationType.class);
        for (NotificationHandler h : handlerList) {
            this.handlers.put(h.type(), h);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(NotificationRequest request) {
        dispatch(request, loadPreferences(request.userSub()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(NotificationRequest request, NotificationPreference prefs) {
        prepare(request, prefs).ifPresent(prep -> persister.persistBatch(List.of(prep)));
    }

    /**
     * Dispatches many requests, chunked by the configured fanout page size; per-request prepare
     * failures are isolated and counted rather than aborting the batch.
     */
    public BatchResult dispatchBatched(List<NotificationRequest> requests) {
        if (requests.isEmpty()) return new BatchResult(0, 0);
        int pageSize = dispatchProperties.fanout().pageSize();
        int dispatched = 0;
        int failed = 0;
        for (int from = 0; from < requests.size(); from += pageSize) {
            int to = Math.min(from + pageSize, requests.size());
            ChunkOutcome outcome = dispatchChunk(requests.subList(from, to));
            dispatched += outcome.dispatched();
            failed += outcome.failed();
        }
        return new BatchResult(dispatched, failed);
    }

    private ChunkOutcome dispatchChunk(List<NotificationRequest> chunk) {
        Set<String> subs = new HashSet<>(chunk.size());
        for (NotificationRequest req : chunk) subs.add(req.userSub());
        Map<String, NotificationPreference> prefsBySub = loadPreferencesBulk(subs);
        Map<String, UserPreferenceSnapshot> userPrefs = userPreferenceCacheService.loadAll(subs);
        Map<String, Boolean> statuses = userStatus.activeStatusOf(subs);

        int dispatched = 0;
        int failed = 0;
        List<Prepared> batch = new ArrayList<>(chunk.size());
        for (NotificationRequest req : chunk) {
            try {
                Optional<Prepared> prep = prepare(req,
                        prefsBySub.get(req.userSub()),
                        userPrefs.get(req.userSub()),
                        statuses.getOrDefault(req.userSub(), true));
                if (prep.isPresent()) {
                    batch.add(prep.get());
                    dispatched++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Batched dispatch prepare failed user={} type={}: {}",
                        req.userSub(), req.type(), ex.getMessage());
            }
        }
        if (!batch.isEmpty()) persister.persistBatch(batch);
        return new ChunkOutcome(dispatched, failed);
    }

    private Map<String, NotificationPreference> loadPreferencesBulk(Set<String> subs) {
        Map<String, NotificationPreference> persisted = preferenceRepository.findAllById(subs).stream()
                .collect(Collectors.toMap(NotificationPreference::getUserSub, Function.identity()));
        Map<String, NotificationPreference> result = new HashMap<>(subs.size());
        for (String sub : subs) {
            result.put(sub, persisted.getOrDefault(sub, NotificationPreference.defaultsFor(sub)));
        }
        return result;
    }

    /** Outcome of a batched dispatch: rows that became deliverable versus prepare failures. */
    public record BatchResult(int dispatched, int failed) {}

    private record ChunkOutcome(int dispatched, int failed) {}

    public Optional<Prepared> prepare(NotificationRequest request, NotificationPreference prefs) {
        UserPreferenceSnapshot snapshot = userPreferenceCacheService.loadAll(List.of(request.userSub()))
                .getOrDefault(request.userSub(), UserPreferenceSnapshot.defaults());
        boolean active = userStatus.isActive(request.userSub());
        return prepare(request, prefs, snapshot, active);
    }

    /**
     * Renders and builds the deliverable artifacts for one request without persisting them. Returns
     * empty when there is no handler, the user is inactive, or neither in-app nor email is wanted.
     */
    public Optional<Prepared> prepare(NotificationRequest request,
                                      NotificationPreference prefs,
                                      UserPreferenceSnapshot snapshot,
                                      boolean active) {
        NotificationHandler handler = handlers.get(request.type());
        if (handler == null) {
            log.warn("No handler registered for type={}; dropping dispatch for user={}", request.type(), request.userSub());
            return Optional.empty();
        }
        if (!active) {
            log.debug("Notification suppressed (user inactive) user={} type={}", request.userSub(), request.type());
            return Optional.empty();
        }
        UserPreferenceSnapshot resolved = snapshot != null ? snapshot : UserPreferenceSnapshot.defaults();
        RenderedNotification rendered = handler.render(request, resolved.locale());
        Notification inapp = prefs.wantsInApp(request.type()) ? buildInApp(request, rendered) : null;
        EmailOutbox outboxRow = prefs.wantsEmail(request.type()) ? buildOutbox(request, rendered, resolved) : null;
        if (inapp == null && outboxRow == null) return Optional.empty();
        return Optional.of(new Prepared(request.userSub(), inapp, outboxRow));
    }

    private Notification buildInApp(NotificationRequest request, RenderedNotification rendered) {
        return Notification.create(
                request.userSub(),
                request.type(),
                rendered.title(),
                rendered.body(),
                request.payload().toMetadata(),
                request.expiresAt());
    }

    private EmailOutbox buildOutbox(NotificationRequest request, RenderedNotification rendered, UserPreferenceSnapshot resolved) {
        Optional<String> emailOpt = userEmailLookup.findEmail(request.userSub());
        if (emailOpt.isEmpty()) {
            log.debug("Email skipped (no address resolved) user={} type={}", request.userSub(), request.type());
            return null;
        }
        return buildOutboxRow(emailOpt.get(), resolved.theme(), resolved.locale(), rendered);
    }

    private EmailOutbox buildOutboxRow(String to, String theme, Locale locale, RenderedNotification rendered) {
        return EmailOutbox.builder()
                .recipientEmail(to)
                .subject(rendered.emailSubject())
                .templateName(rendered.emailTemplate())
                .model(objectMapper.valueToTree(rendered.emailModel()))
                .theme(theme)
                .locale(locale.toLanguageTag())
                .status(EmailOutbox.Status.PENDING)
                .build();
    }

    private NotificationPreference loadPreferences(String userSub) {
        return preferenceRepository.findById(userSub)
                .orElseGet(() -> NotificationPreference.defaultsFor(userSub));
    }
}
