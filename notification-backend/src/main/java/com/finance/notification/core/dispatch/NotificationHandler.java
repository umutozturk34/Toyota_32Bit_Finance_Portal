package com.finance.notification.core.dispatch;

import com.finance.notification.core.model.NotificationType;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

public interface NotificationHandler {

    NotificationType type();

    RenderedNotification render(NotificationRequest request, Locale locale);

    default RenderedNotification render(NotificationRequest request) {
        return render(request, LocaleContextHolder.getLocale());
    }
}
