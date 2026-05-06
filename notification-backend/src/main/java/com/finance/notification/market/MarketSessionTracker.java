package com.finance.notification.market;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Caches the last observed session per {@link SessionMarket} so the listener can
 * detect CLOSED→OPEN transitions across consecutive Kafka {@code market.updated}
 * events. TTL is generous (24h) so a short consumer outage does not lose state;
 * Caffeine is used to satisfy the project rule against raw {@code ConcurrentHashMap}.
 */
@Log4j2
@Component
public class MarketSessionTracker {

    private static final Duration STATE_TTL = Duration.ofHours(24);

    private final Cache<SessionMarket, MarketSession> previous = Caffeine.newBuilder()
            .expireAfterWrite(STATE_TTL)
            .maximumSize(64)
            .build();

    public Optional<MarketSession> previous(SessionMarket market) {
        return Optional.ofNullable(previous.getIfPresent(market));
    }

    public void update(SessionMarket market, MarketSession session) {
        previous.put(market, session);
        log.debug("Session tracker updated market={} session={}", market, session);
    }
}
