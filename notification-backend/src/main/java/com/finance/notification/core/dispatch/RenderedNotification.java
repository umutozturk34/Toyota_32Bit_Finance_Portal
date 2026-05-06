package com.finance.notification.core.dispatch;

import java.util.Map;

public record RenderedNotification(
        String title,
        String body,
        String emailSubject,
        String emailTemplate,
        Map<String, Object> emailModel
) {
}
