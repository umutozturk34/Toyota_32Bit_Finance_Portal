package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Watchlist limits: maximum lists per user and maximum items per list. */
@ConfigurationProperties("notification.watchlist")
public record WatchlistManagementProperties(
        int maxPerUser,
        int maxItemsPerList
) {}
