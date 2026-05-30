package com.finance.notification.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Defines the per-consumer dedup caches of already-processed Kafka event ids. Each listener uses its
 * own named cache so that consuming the same event on multiple topics doesn't suppress one another,
 * giving each consumer independent at-most-once handling within the TTL window.
 */
@Configuration
@RequiredArgsConstructor
public class IdempotencyCacheConfig {

    private final NotificationCacheProperties properties;

    private Duration dedupTtl() {
        return Duration.ofHours(properties.dedupTtlHours());
    }

    @Bean("processedEventIds")
    public Cache<String, Boolean> processedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(dedupTtl())
                .maximumSize(properties.dedupMaxEntries())
                .build();
    }

    @Bean("dataUpdatedProcessedEventIds")
    public Cache<String, Boolean> dataUpdatedProcessedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(dedupTtl())
                .maximumSize(properties.dedupMaxEntries())
                .build();
    }

    @Bean("newsPublishedProcessedEventIds")
    public Cache<String, Boolean> newsPublishedProcessedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(dedupTtl())
                .maximumSize(properties.dedupMaxEntries())
                .build();
    }

    @Bean("portfolioUpdatedProcessedEventIds")
    public Cache<String, Boolean> portfolioUpdatedProcessedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(dedupTtl())
                .maximumSize(properties.dedupMaxEntries())
                .build();
    }

    @Bean("macroIndicatorsUpdatedProcessedEventIds")
    public Cache<String, Boolean> macroIndicatorsUpdatedProcessedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(dedupTtl())
                .maximumSize(properties.dedupMaxEntries())
                .build();
    }
}
