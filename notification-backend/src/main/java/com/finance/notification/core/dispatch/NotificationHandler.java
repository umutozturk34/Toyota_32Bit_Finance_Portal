package com.finance.notification.core.dispatch;

import com.finance.notification.core.model.NotificationType;

import java.util.Locale;

public interface NotificationHandler {

    NotificationType type();

    RenderedNotification render(NotificationRequest request, Locale locale);

    default RenderedNotification render(NotificationRequest request) {
        return render(request, Locale.ENGLISH);
    }
}
