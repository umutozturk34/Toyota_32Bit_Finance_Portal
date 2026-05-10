package com.finance.notification.core.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SystemHandler implements NotificationHandler {

    private final Translator translator;

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

        String title = p.title() != null ? p.title() : translator.translate("notif.system.fallbackTitle");
        String body = p.body() != null ? p.body() : "";

        return new RenderedNotification(
                title,
                body,
                translator.translate("notif.email.subject", title),
                "system",
                Map.of("title", title, "body", body));
    }
}
