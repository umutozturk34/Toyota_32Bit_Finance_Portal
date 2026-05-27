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

    @Bean("userRegisteredProcessedEventIds")
    public Cache<String, Boolean> userRegisteredProcessedEventIds() {
        return Caffeine.newBuilder()
                .expireAfterWrite(dedupTtl())
                .maximumSize(properties.dedupMaxEntries())
                .build();
    }
}
