package com.finance.user.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeInitiateRequest(
        @NotBlank(message = "redirectUri is required")
        String redirectUri
) {
}
