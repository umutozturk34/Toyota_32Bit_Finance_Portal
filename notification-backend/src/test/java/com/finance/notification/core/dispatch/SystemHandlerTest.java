package com.finance.notification.core.dispatch;

import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemHandlerTest {

    private final SystemHandler handler = new SystemHandler();

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
