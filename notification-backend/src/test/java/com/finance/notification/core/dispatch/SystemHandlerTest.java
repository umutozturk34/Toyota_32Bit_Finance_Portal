package com.finance.notification.core.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.testsupport.HandlerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemHandlerTest {

    private SystemHandler handler;

    @BeforeEach
    void setUp() {
        Translator translator = HandlerTestSupport.turkishTranslator();
        handler = new SystemHandler(translator);
    }

    @AfterEach
    void tearDown() {
        HandlerTestSupport.resetLocale();
    }

    @Test
    void type_returnsSystem() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.SYSTEM);
    }

    @Test
    void render_buildsTitleAndBodyFromPayload() {
        SystemPayload payload = new SystemPayload("Bakım", "Yarın 02:00", "admin-1");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Bakım");
        assertThat(result.body()).isEqualTo("Yarın 02:00");
        assertThat(result.emailTemplate()).isEqualTo("system");
        assertThat(result.emailSubject()).contains("Bakım");
        assertThat(result.emailModel()).containsEntry("title", "Bakım");
        assertThat(result.emailModel()).containsEntry("body", "Yarın 02:00");
    }

    @Test
    void render_fallsBackToDefaultsWhenFieldsMissing() {
        SystemPayload payload = new SystemPayload(null, null, null);

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Sistem duyurusu");
        assertThat(result.body()).isEmpty();
    }
}
