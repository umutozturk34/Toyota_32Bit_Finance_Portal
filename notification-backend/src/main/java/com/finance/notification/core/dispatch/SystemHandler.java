package com.finance.notification.core.dispatch;

import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SystemHandler implements NotificationHandler {

    @Override
    public NotificationType type() {
        return NotificationType.SYSTEM;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        Map<String, Object> data = request.data();
        String title = stringValue(data, "title", "Sistem duyurusu");
        String body = stringValue(data, "body", "");

        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + title,
                "system",
                Map.of("title", title, "body", body));
    }

    private static String stringValue(Map<String, Object> data, String key, String fallback) {
        Object value = data.get(key);
        return value == null ? fallback : value.toString();
    }
}
