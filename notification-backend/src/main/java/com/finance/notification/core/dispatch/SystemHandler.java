package com.finance.notification.core.dispatch;

import com.finance.notification.core.dispatch.payload.SystemPayload;
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
        if (!(request.payload() instanceof SystemPayload p)) {
            throw new IllegalArgumentException(
                    "SystemHandler expects SystemPayload, got " + request.payload().getClass().getSimpleName());
        }

        String title = p.title() != null ? p.title() : "Sistem duyurusu";
        String body = p.body() != null ? p.body() : "";

        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + title,
                "system",
                Map.of("title", title, "body", body));
    }
}
