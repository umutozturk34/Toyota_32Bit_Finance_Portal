package com.finance.notification.messaging.dto;

import java.time.LocalDateTime;

public record ConversationSummary(
        String userSub,
        String lastBody,
        LocalDateTime lastSentAt,
        boolean closed
) {
}
