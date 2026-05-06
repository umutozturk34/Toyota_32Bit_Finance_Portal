package com.finance.notification.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageSendRequest(
        @NotBlank(message = "body is required")
        @Size(max = 2000, message = "body must be at most 2000 chars")
        String body
) {
}
