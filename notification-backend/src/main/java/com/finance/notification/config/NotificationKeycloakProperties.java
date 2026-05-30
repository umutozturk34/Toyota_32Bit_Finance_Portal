package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Keycloak admin-lookup tuning: user-search result cap and token-expiry fallback. */
@ConfigurationProperties("notification.keycloak")
public record NotificationKeycloakProperties(
        Integer searchMaxCap,
        Long tokenFallbackExpiresSeconds
) {

    public NotificationKeycloakProperties {
        if (searchMaxCap == null) searchMaxCap = 200;
        if (tokenFallbackExpiresSeconds == null) tokenFallbackExpiresSeconds = 60L;
    }
}
