package com.finance.notification.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMessageSendRequest(
        @NotBlank(message = "{validation.adminMessage.recipientSub.required}")
        @Size(max = 64, message = "{validation.adminMessage.recipientSub.maxLen}")
        String recipientSub,

        @NotBlank(message = "{validation.message.body.required}")
        @Size(max = 2000, message = "{validation.message.body.maxLen}")
        String body
) {
}
