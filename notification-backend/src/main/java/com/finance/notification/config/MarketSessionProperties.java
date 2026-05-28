package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Market-session tuning: how many days ahead to compute sessions and the rate-limit capacity for
 * the public market-status read endpoint.
 */
@ConfigurationProperties("notification.market-session")
public record MarketSessionProperties(
        int lookaheadDays,
        long statusReadCapacityPerMinute
) {}
