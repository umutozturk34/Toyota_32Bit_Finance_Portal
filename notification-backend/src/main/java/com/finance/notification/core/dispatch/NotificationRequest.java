package com.finance.notification.core.dispatch;

import com.finance.notification.core.dispatch.payload.NotificationPayload;
import com.finance.notification.core.model.NotificationType;

import java.time.LocalDateTime;

public record NotificationRequest(
        String userSub,
        NotificationPayload payload,
        LocalDateTime expiresAt
) {
    public NotificationType type() {
        return payload.type();
    }

    public static NotificationRequest of(String userSub, NotificationPayload payload) {
        return new NotificationRequest(userSub, payload, null);
    }
}
