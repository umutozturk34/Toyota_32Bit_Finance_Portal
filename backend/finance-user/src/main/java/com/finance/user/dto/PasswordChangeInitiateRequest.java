package com.finance.user.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeInitiateRequest(
        @NotBlank(message = "{validation.redirectUri.required}")
        String redirectUri
) {
}
