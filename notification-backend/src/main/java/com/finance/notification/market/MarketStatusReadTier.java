package com.finance.notification.market;

import com.finance.common.config.AppProperties;
import com.finance.common.filter.RateLimitTier;
import com.finance.notification.config.MarketSessionProperties;
import io.github.bucket4j.Bandwidth;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate-limit tier for GET {@code /api/v1/market-status}, applying a configurable per-minute capacity
 * to the frequently-polled market-status reads independently of the global limit.
 */
@Component
@Order(22)
@RequiredArgsConstructor
public class MarketStatusReadTier implements RateLimitTier {

    private final MarketSessionProperties sessionProperties;

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
        long capacity = sessionProperties.statusReadCapacityPerMinute();
        return Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(1))
                .build();
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_MARKET_STATUS_EXCEEDED";
    }

    @Override
    public String errorMessage() {
        return "error.rateLimit.marketStatusRead";
    }
}
