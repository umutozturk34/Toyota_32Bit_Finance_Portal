package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageHandlerTest {

    private final MessageHandler handler = new MessageHandler();

    @Test
    void type_returnsMessage() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.MESSAGE);
    }

    @Test
    void render_buildsTitleAndPreviewFromBody() {
        NotificationRequest request = NotificationRequest.of("u",
                NotificationType.MESSAGE,
                Map.of("senderSub", "admin-1", "body", "kısa mesaj"));

        RenderedNotification result = handler.render(request);

        assertThat(result.title()).isEqualTo("Yeni mesaj");
        assertThat(result.body()).isEqualTo("kısa mesaj");
        assertThat(result.emailTemplate()).isEqualTo("message");
        assertThat(result.emailModel()).containsEntry("senderSub", "admin-1");
        assertThat(result.emailModel()).containsEntry("preview", "kısa mesaj");
    }

    @Test
    void render_truncatesLongBodyForPreview() {
        String longBody = "a".repeat(200);
        NotificationRequest request = NotificationRequest.of("u",
                NotificationType.MESSAGE,
                Map.of("senderSub", "admin-1", "body", longBody));

        RenderedNotification result = handler.render(request);

        assertThat(result.body()).hasSize(121);
        assertThat(result.body()).endsWith("…");
    }

    @Test
    void render_fallsBackWhenSenderSubMissing() {
        NotificationRequest request = NotificationRequest.of("u",
                NotificationType.MESSAGE,
                Map.of("body", "no sender data"));

        RenderedNotification result = handler.render(request);

        assertThat(result.emailModel()).containsEntry("senderSub", "anonymous");
    }
}
