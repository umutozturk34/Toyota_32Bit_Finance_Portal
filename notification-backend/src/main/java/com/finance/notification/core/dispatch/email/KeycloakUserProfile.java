package com.finance.notification.core.dispatch.email;

public record KeycloakUserProfile(
        String sub,
        String username,
        String email,
        String firstName,
        String lastName
) {
}
