package com.finance.notification.market.session;

import com.finance.notification.config.NotificationCacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Remembers the last observed session per market (TTL-bounded Caffeine cache) so the scheduler can
 * detect open/close edges across scans. An absent entry means no prior observation, suppressing a
 * spurious transition on startup.
 */
@Log4j2
@Component
public class MarketSessionTracker {

    private final Cache<SessionMarket, MarketSession> previous;

    /**
     * Builds the backing cache sized and expired from notification cache properties (TTL in hours,
     * max tracked markets), so stale session state ages out rather than pinning memory.
     */
    public MarketSessionTracker(NotificationCacheProperties cacheProperties) {
        this.previous = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(cacheProperties.sessionTrackerTtlHours()))
                .maximumSize(cacheProperties.sessionTrackerMaxMarkets())
                .build();
    }

    /**
     * Returns the last recorded session for the market, or empty when none has been observed yet
     * (or the entry has expired). An empty result lets callers suppress a spurious open/close
     * transition on the first scan after startup.
     */
    public Optional<MarketSession> previous(SessionMarket market) {
        return Optional.ofNullable(previous.getIfPresent(market));
    }

    /** Records the market's current session as the new baseline for the next scan's edge detection. */
    public void update(SessionMarket market, MarketSession session) {
        previous.put(market, session);
        log.debug("Session tracker updated market={} session={}", market, session);
    }
}
