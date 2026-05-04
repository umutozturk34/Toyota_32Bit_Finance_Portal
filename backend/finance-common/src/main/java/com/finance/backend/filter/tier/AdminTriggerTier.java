package com.finance.backend.filter.tier;

import com.finance.backend.config.AppProperties;
import com.finance.backend.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(10)
public class AdminTriggerTier implements RateLimitTier {

    @Override
    public String name() {
        return "ADMIN_TRIGGER";
    }

    @Override
    public boolean matches(String path, String method) {
        return path.startsWith("/api/v1/admin/trigger") && "POST".equalsIgnoreCase(method);
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(rl.getAdminTriggerLimit())
                .refillIntervally(rl.getAdminTriggerLimit(), Duration.ofHours(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_ADMIN_TRIGGER_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "Admin güncelleme tetikleme sınırına ulaştın. Lütfen daha sonra tekrar dene.";
    }
}
