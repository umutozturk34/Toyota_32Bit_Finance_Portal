package com.finance.notification.core.dto;

import com.finance.notification.core.model.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

/** Client-facing view of a notification, including its JSONB metadata and read/expiry timestamps. */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        Map<String, Object> metadata,
        LocalDateTime readAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
