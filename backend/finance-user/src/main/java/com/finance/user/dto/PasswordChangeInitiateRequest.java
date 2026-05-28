package com.finance.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Starts a password change via Keycloak's update-password action; {@code redirectUri} returns the user to the app afterward. */
public record PasswordChangeInitiateRequest(
        @NotBlank(message = "{validation.redirectUri.required}")
        String redirectUri
) {
}
