package com.finance.notification.messaging.dto;

import java.time.LocalDateTime;

public record ConversationSummary(
        String userSub,
        String username,
        String email,
        String lastBody,
        LocalDateTime lastSentAt,
        boolean closed,
        long unreadCount
) {
}
