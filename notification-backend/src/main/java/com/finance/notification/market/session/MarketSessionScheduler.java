package com.finance.notification.market.session;

import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.dispatch.payload.MarketClosedPayload;
import com.finance.notification.core.dispatch.payload.MarketOpenedPayload;
import com.finance.notification.core.dispatch.payload.NotificationPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * Minute-by-minute scan that detects market open/close transitions by comparing each market's current
 * session against the last-seen value in the tracker, firing an opened/closed fanout only on a real
 * edge (and never on the first observation, which seeds the tracker without notifying).
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class MarketSessionScheduler {

    private static final String MINUTE_CRON = "0 * * * * *";

    private final MarketSessionResolver resolver;
    private final MarketSessionTracker tracker;
    private final NotificationFanoutService fanoutService;
    private final NotificationPreferenceRepository preferences;
    private final Clock clock;

    @Scheduled(cron = MINUTE_CRON, zone = "Europe/Istanbul")
    public void scanTransitions() {
        Instant now = clock.instant();
        for (SessionMarket market : SessionMarket.values()) {
            Optional<MarketSession> currentOpt = resolver.resolve(market, now);
            if (currentOpt.isEmpty()) continue;
            MarketSession current = currentOpt.get();
            Optional<MarketSession> previous = tracker.previous(market);
            tracker.update(market, current);
            if (previous.isEmpty() || previous.get() == current) continue;
            dispatchTransition(market, current);
        }
    }

    private void dispatchTransition(SessionMarket market, MarketSession current) {
        NotificationPayload payload = (current == MarketSession.OPEN)
                ? new MarketOpenedPayload(market.name())
                : new MarketClosedPayload(market.name());
        Function<Pageable, Page<NotificationPreference>> pageFetcher = (current == MarketSession.OPEN)
                ? p -> preferences.findMarketOpenedSubscribed(market.name(), p)
                : p -> preferences.findMarketClosedSubscribed(market.name(), p);
        FanoutResult result = fanoutService.fanout(
                "market." + market.name().toLowerCase() + "." + current.name().toLowerCase(),
                pageFetcher,
                pref -> Optional.of(payload));
        log.info("Market session transition market={} new={} dispatched={} failed={}",
                market, current, result.dispatched(), result.failed());
    }
}
