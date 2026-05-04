package com.finance.common.filter.tier;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiTier implements RateLimitTier {

    @Override
    public String name() {
        return "API";
    }

    @Override
    public boolean matches(String path, String method) {
        return true;
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(rl.getApiLimit())
                .refillGreedy(rl.getApiLimit(), Duration.ofMinutes(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_API_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "API istek sınırına ulaştın. Lütfen biraz bekleyip tekrar dene.";
    }
}
