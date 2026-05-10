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
                .capacity(EFFECTIVELY_UNLIMITED_PER_MINUTE)
                .refillGreedy(EFFECTIVELY_UNLIMITED_PER_MINUTE, Duration.ofMinutes(1))
                .build();
    }

    private static final long EFFECTIVELY_UNLIMITED_PER_MINUTE = 1_000_000L;

    @Override
    public String errorCode() {
        return "RATE_LIMIT_MESSAGING_PRESENCE_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "error.rateLimit.messagingPresence";
    }
}
