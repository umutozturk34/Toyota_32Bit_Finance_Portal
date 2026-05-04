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
}
