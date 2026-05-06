package com.finance.notification.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class IdempotencyCacheConfig {

    @Bean("processedEventIds")
    public Cache<String, Boolean> processedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(50_000)
                .build();
    }

    /**
     * Independent cache for {@code MarketDataUpdateListener}: shares the topic
     * with {@code MarketUpdateEventListener} (alert/watchlist) but each runs in
     * its own consumer group with independent processing semantics, so a single
     * shared cache would let whichever listener arrived first silence the
     * other. Keeping caches separate guarantees both pipelines fire.
     */
    @Bean("dataUpdatedProcessedEventIds")
    public Cache<String, Boolean> dataUpdatedProcessedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(50_000)
                .build();
    }
}
