package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MessagePayload;
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
        if (!(request.payload() instanceof MessagePayload p)) {
            throw new IllegalArgumentException(
                    "MessageHandler expects MessagePayload, got " + request.payload().getClass().getSimpleName());
        }

        String senderSub = p.senderSub() != null ? p.senderSub() : "anonymous";
        String preview = preview(p.body() != null ? p.body() : "");

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
}
