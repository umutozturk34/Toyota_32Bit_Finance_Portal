package com.finance.notification.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageSendRequest(
        @NotBlank(message = "{validation.message.body.required}")
        @Size(max = 2000, message = "{validation.message.body.maxLen}")
        String body
) {
}
