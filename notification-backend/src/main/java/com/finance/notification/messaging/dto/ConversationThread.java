package com.finance.notification.messaging.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationThread(
        String userSub,
        String username,
        String email,
        boolean closed,
        LocalDateTime closedAt,
        List<MessageResponse> messages
) {
}
