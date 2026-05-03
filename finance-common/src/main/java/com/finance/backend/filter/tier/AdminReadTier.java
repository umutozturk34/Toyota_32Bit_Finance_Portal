package com.finance.backend.filter.tier;

import com.finance.backend.config.AppProperties;
import com.finance.backend.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(20)
public class AdminReadTier implements RateLimitTier {

    @Override
    public String name() {
        return "ADMIN_READ";
    }

    @Override
    public boolean matches(String path, String method) {
        return path.startsWith("/api/v1/admin");
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(rl.getAdminReadLimit())
                .refillIntervally(rl.getAdminReadLimit(), Duration.ofMinutes(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_ADMIN_READ_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "Admin okuma isteği sınırına ulaştın. Lütfen kısa bir süre sonra tekrar dene.";
    }
}
