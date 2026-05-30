package com.finance.notification.core.dispatch;

import com.finance.common.security.UserStatusPort;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.payload.NotificationPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.user.UserPreferenceCacheService;
import com.finance.notification.user.UserPreferenceCacheService.UserPreferenceSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Fans a domain event out to many recipients by paging over notification-preference rows and
 * delegating each page to the dispatcher's prepare/persist path. The bulk variant resolves payloads
 * for a whole page at once; the per-recipient variant builds an optional payload per user (skipping
 * those that yield none). Page-level prepare failures are counted, not propagated.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class NotificationFanoutService {

    private final NotificationDispatcher dispatcher;
    private final NotificationPersister persister;
    private final NotificationDispatchProperties dispatchProperties;
    private final UserPreferenceCacheService userPreferenceCacheService;
    private final UserStatusPort userStatus;

    /**
     * Fans out using a resolver that produces the payload map for an entire page in one call,
     * keyed by user subject; recipients absent from the map are skipped.
     */
    @Transactional
    public <P extends NotificationPayload> FanoutResult fanoutBulk(
            String eventLabel,
            Function<Pageable, Page<NotificationPreference>> pageFetcher,
            Function<List<NotificationPreference>, Map<String, P>> pagePayloadResolver) {
        return runPaginated(pageFetcher, page -> processPage(eventLabel, page, pagePayloadResolver.apply(page)));
    }

    /**
     * Fans out using a per-recipient payload factory; a recipient for which the factory returns
     * empty receives nothing.
     */
    @Transactional
    public <P extends NotificationPayload> FanoutResult fanout(
            String eventLabel,
            Function<Pageable, Page<NotificationPreference>> pageFetcher,
            Function<NotificationPreference, Optional<P>> payloadFactory) {
        return runPaginated(pageFetcher, page -> processPage(eventLabel, page, mapPayloads(page, payloadFactory)));
    }

    private <P extends NotificationPayload> Map<String, P> mapPayloads(
            List<NotificationPreference> page,
            Function<NotificationPreference, Optional<P>> payloadFactory) {
        Map<String, P> payloads = new java.util.HashMap<>(page.size());
        for (NotificationPreference pref : page) {
            payloadFactory.apply(pref).ifPresent(payload -> payloads.put(pref.getUserSub(), payload));
        }
        return payloads;
    }

    private FanoutResult runPaginated(
            Function<Pageable, Page<NotificationPreference>> pageFetcher,
            Function<List<NotificationPreference>, PageOutcome> pageHandler) {
        int pageSize = dispatchProperties.fanout().pageSize();
        int dispatched = 0;
        int failed = 0;
        int pageIndex = 0;
        Page<NotificationPreference> page;
        do {
            page = pageFetcher.apply(PageRequest.of(pageIndex, pageSize));
            PageOutcome outcome = pageHandler.apply(page.getContent());
            dispatched += outcome.dispatched();
            failed += outcome.failed();
            pageIndex++;
        } while (page.hasNext());
        return new FanoutResult(dispatched, failed);
    }

    private <P extends NotificationPayload> PageOutcome processPage(
            String eventLabel, List<NotificationPreference> content, Map<String, P> payloads) {
        if (payloads.isEmpty()) return PageOutcome.EMPTY;
        Set<String> subs = new HashSet<>(payloads.keySet());
        Map<String, UserPreferenceSnapshot> userPrefs = userPreferenceCacheService.loadAll(subs);
        Map<String, Boolean> statuses = userStatus.activeStatusOf(subs);
        int dispatched = 0;
        int failed = 0;
        List<Prepared> batch = new ArrayList<>(subs.size());
        for (NotificationPreference pref : content) {
            P payload = payloads.get(pref.getUserSub());
            if (payload == null) continue;
            try {
                Optional<Prepared> prep = dispatcher.prepare(
                        NotificationRequest.of(pref.getUserSub(), payload),
                        pref,
                        userPrefs.get(pref.getUserSub()),
                        statuses.getOrDefault(pref.getUserSub(), true));
                if (prep.isPresent()) { batch.add(prep.get()); dispatched++; }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("{} prepare failed user={}: {}", eventLabel, pref.getUserSub(), ex.getMessage());
            }
        }
        if (!batch.isEmpty()) persister.persistBatch(batch);
        return new PageOutcome(dispatched, failed);
    }

    private record PageOutcome(int dispatched, int failed) {
        static final PageOutcome EMPTY = new PageOutcome(0, 0);
    }

    /** Aggregate fanout outcome across all pages: deliverable rows versus per-recipient failures. */
    public record FanoutResult(int dispatched, int failed) {}
}
