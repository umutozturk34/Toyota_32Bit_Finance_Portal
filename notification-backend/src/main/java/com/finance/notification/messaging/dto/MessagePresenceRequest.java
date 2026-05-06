package com.finance.notification.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessagePresenceRequest(
        @NotBlank
        @Size(max = 64)
        String key) {
}
