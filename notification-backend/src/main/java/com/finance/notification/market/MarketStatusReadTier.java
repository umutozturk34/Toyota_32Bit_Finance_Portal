package com.finance.notification.market;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(22)
public class MarketStatusReadTier implements RateLimitTier {

    private static final long CAPACITY_PER_MINUTE = 240L;

    @Override
    public String name() {
        return "MARKET_STATUS_READ";
    }

    @Override
    public boolean matches(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        return path.startsWith("/api/v1/market-status");
    }

    @Override
    public Bandwidth toBandwidth(AppProperties.RateLimit rl) {
        return Bandwidth.builder()
                .capacity(CAPACITY_PER_MINUTE)
                .refillGreedy(CAPACITY_PER_MINUTE, Duration.ofMinutes(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_MARKET_STATUS_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "Piyasa durumu sorgu sınırına ulaştın.";
    }
}
