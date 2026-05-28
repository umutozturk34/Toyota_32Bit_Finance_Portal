package com.finance.notification.core.dispatch;

import com.finance.notification.core.model.NotificationType;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * Strategy that renders a request of a specific {@link NotificationType} into the localized
 * title/body/email content the dispatcher persists. One implementation is registered per type.
 */
public interface NotificationHandler {

    /** The notification type this handler is responsible for; used as its registration key. */
    NotificationType type();

    RenderedNotification render(NotificationRequest request, Locale locale);

    /** Renders using the locale from the current request context. */
    default RenderedNotification render(NotificationRequest request) {
        return render(request, LocaleContextHolder.getLocale());
    }
}
