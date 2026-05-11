package com.finance.notification.messaging.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MessagePayload;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.testsupport.HandlerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageHandlerTest {

    private static final NotificationDispatchProperties DISPATCH = new NotificationDispatchProperties(
            new NotificationDispatchProperties.Formatting(2, 6, 6),
            new NotificationDispatchProperties.WatchlistDelta(3),
            new NotificationDispatchProperties.Message(120),
            new NotificationDispatchProperties.Fanout(200));

    private MessageHandler handler;

    @BeforeEach
    void setUp() {
        Translator translator = HandlerTestSupport.turkishTranslator();
        handler = new MessageHandler(DISPATCH, translator);
    }

    @AfterEach
    void tearDown() {
        HandlerTestSupport.resetLocale();
    }

    @Test
    void type_returnsMessage() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.MESSAGE);
    }

    @Test
    void render_buildsTitleAndPreviewFromBody() {
        MessagePayload payload = new MessagePayload("admin-1", "kısa mesaj");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Yeni mesaj");
        assertThat(result.body()).isEqualTo("kısa mesaj");
        assertThat(result.emailTemplate()).isEqualTo("message");
        assertThat(result.emailModel()).containsEntry("senderSub", "admin-1");
        assertThat(result.emailModel()).containsEntry("preview", "kısa mesaj");
    }

    @Test
    void render_truncatesLongBodyForPreview() {
        MessagePayload payload = new MessagePayload("admin-1", "a".repeat(200));

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.body()).hasSize(121);
        assertThat(result.body()).endsWith("…");
    }

    @Test
    void render_fallsBackWhenSenderSubMissing() {
        MessagePayload payload = new MessagePayload(null, "no sender data");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.emailModel()).containsEntry("senderSub", "anonymous");
    }
}
