package com.finance.notification.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BroadcastRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String body
) {
}
