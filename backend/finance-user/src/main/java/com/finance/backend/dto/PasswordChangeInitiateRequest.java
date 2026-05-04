package com.finance.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeInitiateRequest(
        @NotBlank(message = "redirectUri is required")
        String redirectUri
) {
}
