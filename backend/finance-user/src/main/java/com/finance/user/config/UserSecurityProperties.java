package com.finance.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bound {@code app.user-security.*} configuration governing the email-change verification flow
 * (attempt cap, code length, TTL), password-reset link lifespan, and the Keycloak client/attribute
 * names used when syncing theme and locale.
 */
@ConfigurationProperties("app.user-security")
public record UserSecurityProperties(
        EmailChange emailChange,
        PasswordReset passwordReset,
        Keycloak keycloak
) {

    public record EmailChange(
            int maxAttempts,
            int codeLength,
            Duration codeTtl
    ) {}

    public record PasswordReset(
            long linkLifespanSeconds
    ) {}

    public record Keycloak(
            String frontendClientId,
            String themeAttribute,
            String localeAttribute
    ) {
        public Keycloak {
            localeAttribute = localeAttribute == null ? "locale" : localeAttribute;
        }
    }
}
