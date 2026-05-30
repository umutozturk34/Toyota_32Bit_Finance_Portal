package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Sizes and TTLs for the in-memory caches (event dedup, session tracker, Keycloak profiles). */
@ConfigurationProperties("notification.cache")
public record NotificationCacheProperties(
        long dedupMaxEntries,
        long sessionTrackerMaxMarkets,
        Integer dedupTtlHours,
        Integer sessionTrackerTtlHours,
        Integer keycloakProfileMaxSize,
        Integer keycloakProfileTtlMinutes
) {

    public NotificationCacheProperties {
        if (dedupTtlHours == null) dedupTtlHours = 24;
        if (sessionTrackerTtlHours == null) sessionTrackerTtlHours = 24;
        if (keycloakProfileMaxSize == null) keycloakProfileMaxSize = 2000;
        if (keycloakProfileTtlMinutes == null) keycloakProfileTtlMinutes = 15;
    }
}
