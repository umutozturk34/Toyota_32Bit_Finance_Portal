package com.finance.notification.core.dispatch;

import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.model.Notification;

/**
 * A request that has been rendered and resolved into its deliverable artifacts but not yet saved.
 * Either side may be null when the user opted out of that channel (but never both).
 */
public record Prepared(
        String userSub,
        Notification inappEntity,
        EmailOutbox outboxRow
) {
}
