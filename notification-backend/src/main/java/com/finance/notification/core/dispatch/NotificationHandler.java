package com.finance.notification.core.dispatch;

import com.finance.notification.core.model.NotificationType;

public interface NotificationHandler {

    NotificationType type();

    RenderedNotification render(NotificationRequest request);
}
