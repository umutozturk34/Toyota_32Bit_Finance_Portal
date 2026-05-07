package com.finance.notification.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class IdempotencyCacheConfig {

    private static final Duration EVENT_DEDUP_TTL = Duration.ofHours(24);
    private static final long MAX_DEDUP_ENTRIES = 50_000;

    @Bean("processedEventIds")
    public Cache<String, Boolean> processedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(EVENT_DEDUP_TTL)
                .maximumSize(MAX_DEDUP_ENTRIES)
                .build();
    }

    @Bean("dataUpdatedProcessedEventIds")
    public Cache<String, Boolean> dataUpdatedProcessedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(EVENT_DEDUP_TTL)
                .maximumSize(MAX_DEDUP_ENTRIES)
                .build();
    }
}
