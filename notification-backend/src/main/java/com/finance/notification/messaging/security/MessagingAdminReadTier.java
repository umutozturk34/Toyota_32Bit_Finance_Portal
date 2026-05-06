package com.finance.notification.messaging.security;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(18)
public class MessagingAdminReadTier implements RateLimitTier {

    @Override
    public String name() {
        return "MESSAGING_ADMIN_READ";
    }

    @Override
    public boolean matches(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        return path.startsWith("/api/v1/admin/messages")
                || path.startsWith("/api/v1/admin/notifications");
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
        return "RATE_LIMIT_MESSAGING_ADMIN_READ_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "Admin okuma sınırına ulaştın.";
    }
}
