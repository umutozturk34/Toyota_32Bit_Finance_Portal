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
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
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

    public void preloadPage(Collection<String> userSubs) {
        userPreferenceCacheService.preload(userSubs);
        userStatus.preload(userSubs);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(NotificationRequest request) {
        dispatch(request, loadPreferences(request.userSub()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(NotificationRequest request, NotificationPreference prefs) {
        prepare(request, prefs).ifPresent(prep -> persister.persistBatch(List.of(prep)));
    }

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
        preloadPage(subs);
        Map<String, NotificationPreference> prefsBySub = loadPreferencesBulk(subs);

        int dispatched = 0;
        int failed = 0;
        List<Prepared> batch = new ArrayList<>(chunk.size());
        for (NotificationRequest req : chunk) {
            try {
                Optional<Prepared> prep = prepare(req, prefsBySub.get(req.userSub()));
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

    public record BatchResult(int dispatched, int failed) {}

    private record ChunkOutcome(int dispatched, int failed) {}

    public Optional<Prepared> prepare(NotificationRequest request, NotificationPreference prefs) {
        NotificationHandler handler = handlers.get(request.type());
        if (handler == null) {
            log.warn("No handler registered for type={}; dropping dispatch for user={}", request.type(), request.userSub());
            return Optional.empty();
        }
        if (!userStatus.isActive(request.userSub())) {
            log.debug("Notification suppressed (user inactive) user={} type={}", request.userSub(), request.type());
            return Optional.empty();
        }

        Locale recipientLocale = userPreferenceCacheService.resolveLocale(request.userSub());
        RenderedNotification rendered = handler.render(request, recipientLocale);

        Notification inapp = null;
        if (prefs.wantsInApp(request.type())) {
            inapp = Notification.create(
                    request.userSub(),
                    request.type(),
                    rendered.title(),
                    rendered.body(),
                    request.payload().toMetadata(),
                    request.expiresAt());
        }

        EmailOutbox outboxRow = null;
        if (prefs.wantsEmail(request.type())) {
            Optional<String> emailOpt = userEmailLookup.findEmail(request.userSub());
            if (emailOpt.isEmpty()) {
                log.debug("Email skipped (no address resolved) user={} type={}",
                        request.userSub(), request.type());
            } else {
                String theme = userPreferenceCacheService.resolveTheme(request.userSub());
                outboxRow = buildOutboxRow(emailOpt.get(), theme, recipientLocale, rendered);
            }
        }

        if (inapp == null && outboxRow == null) return Optional.empty();
        return Optional.of(new Prepared(request.userSub(), inapp, outboxRow));
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
