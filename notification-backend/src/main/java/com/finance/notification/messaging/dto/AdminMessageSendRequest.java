package com.finance.notification.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMessageSendRequest(
        @NotBlank(message = "recipientSub is required")
        @Size(max = 64, message = "recipientSub must be at most 64 chars")
        String recipientSub,

        @NotBlank(message = "body is required")
        @Size(max = 2000, message = "body must be at most 2000 chars")
        String body
) {
}
