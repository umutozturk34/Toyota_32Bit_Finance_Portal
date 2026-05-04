package com.finance.notification.core.dispatch;

import com.finance.notification.core.model.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationRequest(
        String userSub,
        NotificationType type,
        Map<String, Object> data,
        LocalDateTime expiresAt
) {
    public static NotificationRequest of(String userSub, NotificationType type, Map<String, Object> data) {
        return new NotificationRequest(userSub, type, data, null);
    }
}
