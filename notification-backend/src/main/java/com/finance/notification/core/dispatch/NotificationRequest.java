package com.finance.notification.core.dispatch;

import com.finance.notification.core.dispatch.payload.NotificationPayload;
import com.finance.notification.core.model.NotificationType;

import java.time.LocalDateTime;

/**
 * A request to notify a single user, pairing the recipient subject with a typed payload and an
 * optional expiry. The notification type is derived from the payload.
 */
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
