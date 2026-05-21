package com.finance.notification.market.session;

import com.finance.notification.config.NotificationCacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Log4j2
@Component
public class MarketSessionTracker {

    private final Cache<SessionMarket, MarketSession> previous;

    public MarketSessionTracker(NotificationCacheProperties cacheProperties) {
        this.previous = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(cacheProperties.sessionTrackerTtlHours()))
                .maximumSize(cacheProperties.sessionTrackerMaxMarkets())
                .build();
    }

    public Optional<MarketSession> previous(SessionMarket market) {
        return Optional.ofNullable(previous.getIfPresent(market));
    }

    public void update(SessionMarket market, MarketSession session) {
        previous.put(market, session);
        log.debug("Session tracker updated market={} session={}", market, session);
    }
}
