package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.market-session")
public record MarketSessionProperties(
        int lookaheadDays,
        long statusReadCapacityPerMinute
) {}
