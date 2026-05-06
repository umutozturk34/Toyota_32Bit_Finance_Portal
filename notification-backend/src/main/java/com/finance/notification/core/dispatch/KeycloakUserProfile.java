package com.finance.notification.core.dispatch;

public record KeycloakUserProfile(
        String sub,
        String username,
        String email,
        String firstName,
        String lastName
) {
}
