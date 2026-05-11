package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.cache")
public record NotificationCacheProperties(
        long dedupMaxEntries,
        long sessionTrackerMaxMarkets
) {}
