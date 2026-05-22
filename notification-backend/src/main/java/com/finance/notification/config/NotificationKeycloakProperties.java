package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
