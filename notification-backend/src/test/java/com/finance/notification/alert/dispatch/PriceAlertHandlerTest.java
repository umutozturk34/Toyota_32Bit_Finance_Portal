package com.finance.notification.alert.dispatch;

import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PriceAlertHandlerTest {

    private final PriceAlertHandler handler = new PriceAlertHandler();

    @Test
    void type_returnsPriceAlertFired() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.PRICE_ALERT_FIRED);
    }

    @Test
    void render_buildsTitleBodyAndEmailModelFromData() {
        NotificationRequest request = NotificationRequest.of("u",
                NotificationType.PRICE_ALERT_FIRED,
                Map.of(
                        "assetCode", "BTC",
                        "direction", "ABOVE",
                        "threshold", BigDecimal.valueOf(100),
                        "currentPrice", BigDecimal.valueOf(105)
                ));

        RenderedNotification result = handler.render(request);

        assertThat(result.title()).isEqualTo("BTC alarmı tetiklendi");
        assertThat(result.body()).contains("üstüne çıktı");
        assertThat(result.emailTemplate()).isEqualTo("price-alert");
        assertThat(result.emailSubject()).contains("BTC");
        assertThat(result.emailModel()).containsEntry("assetCode", "BTC");
        assertThat(result.emailModel()).containsEntry("assetCodeUpper", "BTC");
        assertThat(result.emailModel()).containsEntry("isUp", true);
    }

    @Test
    void render_fallsBackToSensibleDefaultsWhenDataMissing() {
        NotificationRequest request = NotificationRequest.of("u",
                NotificationType.PRICE_ALERT_FIRED, Map.of());

        RenderedNotification result = handler.render(request);

        assertThat(result.title()).isEqualTo("? alarmı tetiklendi");
        assertThat(result.body()).contains("Kripto");
    }
}
