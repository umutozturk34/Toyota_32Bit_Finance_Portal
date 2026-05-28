package com.finance.notification.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin request to broadcast a system message, with bounded title and body. */
public record BroadcastRequest(
        @NotBlank(message = "{validation.broadcast.title.required}")
        @Size(max = 120, message = "{validation.broadcast.title.maxLen}")
        String title,
        @NotBlank(message = "{validation.broadcast.body.required}")
        @Size(max = 4000, message = "{validation.broadcast.body.maxLen}")
        String body
) {
}
