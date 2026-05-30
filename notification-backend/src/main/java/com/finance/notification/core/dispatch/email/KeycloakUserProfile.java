package com.finance.notification.core.dispatch.email;

/** Minimal user profile projected from Keycloak: subject plus username, email and display name. */
public record KeycloakUserProfile(
        String sub,
        String username,
        String email,
        String firstName,
        String lastName
) {
}
