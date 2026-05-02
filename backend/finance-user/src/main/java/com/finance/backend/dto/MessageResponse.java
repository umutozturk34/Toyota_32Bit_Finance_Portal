package com.finance.backend.dto;

import com.finance.backend.dto.enums.MessageDirection;

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
