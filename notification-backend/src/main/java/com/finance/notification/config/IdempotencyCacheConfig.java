package com.finance.notification.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class IdempotencyCacheConfig {

    private static final Duration EVENT_DEDUP_TTL = Duration.ofHours(24);

    private final NotificationCacheProperties properties;

    @Bean("processedEventIds")
    public Cache<String, Boolean> processedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(EVENT_DEDUP_TTL)
                .maximumSize(properties.dedupMaxEntries())
                .build();
    }

    @Bean("dataUpdatedProcessedEventIds")
    public Cache<String, Boolean> dataUpdatedProcessedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(EVENT_DEDUP_TTL)
                .maximumSize(properties.dedupMaxEntries())
                .build();
    }
}
