package com.finance.notification.market;

import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.MarketClosedPayload;
import com.finance.notification.core.dispatch.payload.MarketOpenedPayload;
import com.finance.notification.core.dispatch.payload.NotificationPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Log4j2
@Component
@RequiredArgsConstructor
public class MarketSessionScheduler {

    private static final String MINUTE_CRON = "0 * * * * *";

    private final MarketSessionResolver resolver;
    private final MarketSessionTracker tracker;
    private final NotificationDispatcher dispatcher;
    private final NotificationPreferenceRepository preferences;
    private final Clock clock;

    @Scheduled(cron = MINUTE_CRON, zone = "Europe/Istanbul")
    public void scanTransitions() {
        Instant now = clock.instant();
        List<NotificationPreference> subscribers = null;
        for (SessionMarket market : SessionMarket.values()) {
            Optional<MarketSession> currentOpt = resolver.resolve(market, now);
            if (currentOpt.isEmpty()) continue;
            MarketSession current = currentOpt.get();
            Optional<MarketSession> previous = tracker.previous(market);
            tracker.update(market, current);
            if (previous.isEmpty() || previous.get() == current) continue;
            if (subscribers == null) subscribers = preferences.findAll();
            dispatchTransition(market, current, subscribers);
        }
    }

    private void dispatchTransition(SessionMarket market, MarketSession current,
                                    List<NotificationPreference> subscribers) {
        NotificationPayload payload = (current == MarketSession.OPEN)
                ? new MarketOpenedPayload(market.name(), market.displayLabel())
                : new MarketClosedPayload(market.name(), market.displayLabel());
        log.info("Market session transition market={} new={} subscribers={}",
                market, current, subscribers.size());
        for (NotificationPreference pref : subscribers) {
            if (!pref.subscribesToMarket(market)) continue;
            dispatcher.dispatch(NotificationRequest.of(pref.getUserSub(), payload));
        }
    }
}
