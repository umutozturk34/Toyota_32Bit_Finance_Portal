package com.finance.notification.messaging.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MessagePayload;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MessageHandler implements NotificationHandler {

    private final NotificationDispatchProperties properties;
    private final Translator translator;

    @Override
    public NotificationType type() {
        return NotificationType.MESSAGE;
    }

    @Override
    public RenderedNotification render(NotificationRequest request, Locale locale) {
        if (!(request.payload() instanceof MessagePayload p)) {
            throw new IllegalArgumentException(
                    "MessageHandler expects MessagePayload, got " + request.payload().getClass().getSimpleName());
        }

        String senderSub = p.senderSub() != null ? p.senderSub() : "anonymous";
        String preview = preview(p.body() != null ? p.body() : "");
        String title = translator.translate("notif.message.title", locale);

        return new RenderedNotification(
                title,
                preview,
                translator.translate("notif.email.subject", locale, title),
                "message",
                Map.of("senderSub", senderSub, "preview", preview));
    }

    private String preview(String body) {
        int max = properties.message().previewMaxChars();
        if (body.length() <= max) {
            return body;
        }
        return body.substring(0, max) + "…";
    }
}
