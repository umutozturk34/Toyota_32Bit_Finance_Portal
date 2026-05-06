package com.finance.notification.market;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Log4j2
@Component
public class MarketSessionTracker {

    private static final Duration STATE_TTL = Duration.ofHours(24);
    private static final int MAX_TRACKED_MARKETS = 64;

    private final Cache<SessionMarket, MarketSession> previous = Caffeine.newBuilder()
            .expireAfterWrite(STATE_TTL)
            .maximumSize(MAX_TRACKED_MARKETS)
            .build();

    public Optional<MarketSession> previous(SessionMarket market) {
        return Optional.ofNullable(previous.getIfPresent(market));
    }

    public void update(SessionMarket market, MarketSession session) {
        previous.put(market, session);
        log.debug("Session tracker updated market={} session={}", market, session);
    }
}
