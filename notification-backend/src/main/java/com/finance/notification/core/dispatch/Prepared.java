package com.finance.notification.core.dispatch;

import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.model.Notification;

public record Prepared(
        String userSub,
        Notification inappEntity,
        EmailOutbox outboxRow
) {
}
