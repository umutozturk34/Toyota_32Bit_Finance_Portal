package com.finance.notification.messaging.security;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(25)
public class MessagingUserTier implements RateLimitTier {

    @Override
    public String name() {
        return "MESSAGING_USER";
    }

    @Override
    public boolean matches(String path, String method) {
        return path.equals("/api/v1/messages") && "POST".equalsIgnoreCase(method);
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(rl.getMessagingUserLimit())
                .refillGreedy(rl.getMessagingUserLimit(), Duration.ofMinutes(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_MESSAGING_USER_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "Mesaj gönderme sınırına ulaştın. Lütfen kısa bir süre sonra tekrar dene.";
    }
}
