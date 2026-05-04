package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MessageHandler implements NotificationHandler {

    private static final int PREVIEW_MAX_CHARS = 120;

    @Override
    public NotificationType type() {
        return NotificationType.MESSAGE;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        Map<String, Object> data = request.data();
        String senderSub = stringValue(data, "senderSub", "anonymous");
        String body = stringValue(data, "body", "");
        String preview = preview(body);

        return new RenderedNotification(
                "Yeni mesaj",
                preview,
                "Finance Portal — yeni mesaj",
                "message",
                Map.of("senderSub", senderSub, "preview", preview));
    }

    private static String preview(String body) {
        if (body.length() <= PREVIEW_MAX_CHARS) {
            return body;
        }
        return body.substring(0, PREVIEW_MAX_CHARS) + "…";
    }

    private static String stringValue(Map<String, Object> data, String key, String fallback) {
        Object value = data.get(key);
        return value == null ? fallback : value.toString();
    }
}
