package com.finance.notification.core.dispatch;

import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemHandlerTest {

    private final SystemHandler handler = new SystemHandler();

    @Test
    void type_returnsSystem() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.SYSTEM);
    }

    @Test
    void render_buildsTitleAndBodyFromData() {
        NotificationRequest request = NotificationRequest.of("u",
                NotificationType.SYSTEM,
                Map.of("title", "Bakım", "body", "Yarın 02:00", "issuedBy", "admin-1"));

        RenderedNotification result = handler.render(request);

        assertThat(result.title()).isEqualTo("Bakım");
        assertThat(result.body()).isEqualTo("Yarın 02:00");
        assertThat(result.emailTemplate()).isEqualTo("system");
        assertThat(result.emailSubject()).contains("Bakım");
        assertThat(result.emailModel()).containsEntry("title", "Bakım");
        assertThat(result.emailModel()).containsEntry("body", "Yarın 02:00");
    }

    @Test
    void render_fallsBackToDefaultsWhenDataMissing() {
        NotificationRequest request = NotificationRequest.of("u", NotificationType.SYSTEM, Map.of());

        RenderedNotification result = handler.render(request);

        assertThat(result.title()).isEqualTo("Sistem duyurusu");
        assertThat(result.body()).isEmpty();
    }
}
