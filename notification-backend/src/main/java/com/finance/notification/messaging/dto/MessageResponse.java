package com.finance.notification.messaging.dto;

import com.finance.notification.messaging.model.MessageDirection;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        String senderSub,
        String recipientSub,
        String body,
        MessageDirection direction,
        LocalDateTime sentAt,
        LocalDateTime readAt
) {
}
