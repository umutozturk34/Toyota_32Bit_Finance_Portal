package com.finance.common.filter.tier;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-minute cap on GET reads of credential/profile endpoints; ordered just ahead of the action
 * tier so reads are classified before the broader mutation matcher.
 */
@Component
@Order(29)
public class CredentialReadTier implements RateLimitTier {

    @Override
    public String name() {
        return "CREDENTIAL_READ";
    }

    @Override
    public boolean matches(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        return path.startsWith("/api/v1/user/credentials")
                || path.startsWith("/api/v1/user/profile");
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(rl.getCredentialReadLimit())
                .refillGreedy(rl.getCredentialReadLimit(), Duration.ofMinutes(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_CREDENTIAL_READ_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "error.rateLimit.credentialRead";
    }
}
