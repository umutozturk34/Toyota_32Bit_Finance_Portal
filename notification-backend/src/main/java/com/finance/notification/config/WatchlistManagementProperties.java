package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.watchlist")
public record WatchlistManagementProperties(
        int maxPerUser
) {}
