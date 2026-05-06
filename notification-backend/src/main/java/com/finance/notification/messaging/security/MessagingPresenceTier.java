package com.finance.notification.messaging.security;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(14)
public class MessagingPresenceTier implements RateLimitTier {

    @Override
    public String name() {
        return "MESSAGING_PRESENCE";
    }

    @Override
    public boolean matches(String path, String method) {
        return path.startsWith("/api/v1/messages/active");
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(Long.MAX_VALUE)
                .refillGreedy(Long.MAX_VALUE, Duration.ofSeconds(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_MESSAGING_PRESENCE_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "Aktif konuşma sınırına ulaştın.";
    }
}
