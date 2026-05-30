package com.finance.notification.core.dispatch;

import java.util.Map;

/**
 * Localized, channel-ready output of a {@link NotificationHandler}: in-app title/body plus the email
 * subject, template name and model used to render the outbound email.
 */
public record RenderedNotification(
        String title,
        String body,
        String emailSubject,
        String emailTemplate,
        Map<String, Object> emailModel
) {
}
