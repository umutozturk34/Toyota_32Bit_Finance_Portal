package com.finance.notification.core.dispatch;

import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.payload.NotificationPayload;
import com.finance.notification.core.model.NotificationPreference;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Log4j2
@Service
@RequiredArgsConstructor
public class NotificationFanoutService {

    private final NotificationDispatcher dispatcher;
    private final NotificationPersister persister;
    private final NotificationDispatchProperties dispatchProperties;

    @Transactional
    public <P extends NotificationPayload> FanoutResult fanout(
            String eventLabel,
            Function<Pageable, Page<NotificationPreference>> pageFetcher,
            Function<NotificationPreference, Optional<P>> payloadFactory) {
        int pageSize = dispatchProperties.fanout().pageSize();
        int dispatched = 0;
        int failed = 0;
        int pageIndex = 0;
        Page<NotificationPreference> page;
        do {
            page = pageFetcher.apply(PageRequest.of(pageIndex, pageSize));
            Set<String> subs = new HashSet<>(page.getNumberOfElements());
            for (NotificationPreference pref : page.getContent()) subs.add(pref.getUserSub());
            dispatcher.preloadPage(subs);

            List<Prepared> batch = new ArrayList<>(page.getNumberOfElements());
            for (NotificationPreference pref : page.getContent()) {
                Optional<P> payloadOpt = payloadFactory.apply(pref);
                if (payloadOpt.isEmpty()) continue;
                try {
                    Optional<Prepared> prep = dispatcher.prepare(
                            NotificationRequest.of(pref.getUserSub(), payloadOpt.get()), pref);
                    if (prep.isPresent()) {
                        batch.add(prep.get());
                        dispatched++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("{} prepare failed user={}: {}",
                            eventLabel, pref.getUserSub(), ex.getMessage());
                }
            }
            if (!batch.isEmpty()) persister.persistBatch(batch);
            pageIndex++;
        } while (page.hasNext());
        return new FanoutResult(dispatched, failed);
    }

    public record FanoutResult(int dispatched, int failed) {}
}
