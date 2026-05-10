package com.finance.notification.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessagePresenceRequest(
        @NotBlank(message = "{validation.messagePresence.threadKey.required}")
        @Size(max = 64, message = "{validation.messagePresence.threadKey.maxLen}")
        String key) {
}
