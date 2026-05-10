package com.finance.notification.messaging.security;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(15)
public class MessagingAdminTier implements RateLimitTier {

    @Override
    public String name() {
        return "MESSAGING_ADMIN";
    }

    @Override
    public boolean matches(String path, String method) {
        if (!"POST".equalsIgnoreCase(method)) return false;
        return path.startsWith("/api/v1/admin/messages")
                || path.startsWith("/api/v1/admin/notifications");
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(rl.getMessagingAdminLimit())
                .refillGreedy(rl.getMessagingAdminLimit(), Duration.ofMinutes(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_MESSAGING_ADMIN_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "error.rateLimit.messagingAdminWrite";
    }
}
