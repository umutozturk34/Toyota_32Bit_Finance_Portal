package com.finance.notification.alert.dispatch;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceAlertHandlerTest {

    private static final NotificationDispatchProperties DISPATCH = new NotificationDispatchProperties(
            new NotificationDispatchProperties.Formatting(2, 6, 6),
            new NotificationDispatchProperties.WatchlistDelta(3),
            new NotificationDispatchProperties.Message(120));

    private final PriceAlertHandler handler = new PriceAlertHandler(DISPATCH);

    @Test
    void type_returnsPriceAlertFired() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.PRICE_ALERT_FIRED);
    }

    @Test
    void render_buildsTitleBodyAndEmailModelFromPayload() {
        PriceAlertPayload payload = new PriceAlertPayload(
                7L, MarketType.CRYPTO, "BTC", AlertDirection.ABOVE,
                BigDecimal.valueOf(100), BigDecimal.valueOf(105), null, null);

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("BTC alarmı tetiklendi");
        assertThat(result.body()).contains("üstüne çıktı");
        assertThat(result.emailTemplate()).isEqualTo("price-alert");
        assertThat(result.emailSubject()).contains("BTC");
        assertThat(result.emailModel()).containsEntry("assetCode", "BTC");
        assertThat(result.emailModel()).containsEntry("assetCodeUpper", "BTC");
        assertThat(result.emailModel()).containsEntry("isUp", true);
    }

    @Test
    void render_usesAssetNameAndImageWhenProvided() {
        PriceAlertPayload payload = new PriceAlertPayload(
                7L, MarketType.CRYPTO, "btc", AlertDirection.BELOW,
                BigDecimal.valueOf(50), BigDecimal.valueOf(40),
                "https://x/y.png", "Bitcoin");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Bitcoin alarmı tetiklendi");
        assertThat(result.emailModel()).containsEntry("assetName", "Bitcoin");
        assertThat(result.emailModel()).containsEntry("image", "https://x/y.png");
        assertThat(result.emailModel()).containsEntry("isUp", false);
    }
}
