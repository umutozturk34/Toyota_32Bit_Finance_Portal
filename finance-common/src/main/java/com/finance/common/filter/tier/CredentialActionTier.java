package com.finance.common.filter.tier;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(30)
public class CredentialActionTier implements RateLimitTier {

    @Override
    public String name() {
        return "CREDENTIAL_ACTION";
    }

    @Override
    public boolean matches(String path, String method) {
        return path.startsWith("/api/v1/user/credentials");
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(rl.getCredentialActionLimit())
                .refillIntervally(rl.getCredentialActionLimit(), Duration.ofHours(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_CREDENTIAL_ACTION_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "Kimlik işlem sınırına ulaştın. Lütfen 1 saat içinde tekrar dene.";
    }
}
