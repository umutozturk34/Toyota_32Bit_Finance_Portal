package com.finance.notification.alert.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.testsupport.HandlerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceAlertHandlerTest {

    private static final NotificationDispatchProperties DISPATCH = new NotificationDispatchProperties(
            new NotificationDispatchProperties.Formatting(2, 6, 6),
            new NotificationDispatchProperties.WatchlistDelta(3),
            new NotificationDispatchProperties.Fanout(200),
            null);

    private PriceAlertHandler handler;

    @BeforeEach
    void setUp() {
        Translator translator = HandlerTestSupport.turkishTranslator();
        handler = new PriceAlertHandler(DISPATCH, translator);
    }

    @AfterEach
    void tearDown() {
        HandlerTestSupport.resetLocale();
    }

    @Test
    void type_returnsPriceAlertFired() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.PRICE_ALERT_FIRED);
    }

    @Test
    void render_buildsTitleBodyAndEmailModelFromPayload() {
        PriceAlertPayload payload = new PriceAlertPayload(
                7L, MarketType.CRYPTO, "BTC", AlertDirection.ABOVE,
                BigDecimal.valueOf(100), BigDecimal.valueOf(105), null, null, "TRY");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("BTC alarmı tetiklendi");
        assertThat(result.body()).contains("üstüne çıkarsa");
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
                "https://x/y.png", "Bitcoin", "TRY");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Bitcoin alarmı tetiklendi");
        assertThat(result.emailModel()).containsEntry("assetName", "Bitcoin");
        assertThat(result.emailModel()).containsEntry("image", "https://x/y.png");
        assertThat(result.emailModel()).containsEntry("isUp", false);
    }

    @Test
    void render_throwsIllegalArgument_whenPayloadIsNotPriceAlert() {
        NotificationRequest request = NotificationRequest.of("u",
                new com.finance.notification.core.dispatch.payload.SystemPayload("t", "b", "ops"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.render(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PriceAlertPayload");
    }

    @Test
    void render_usesFallbackMarketLabel_whenMarketTypeIsNull() {
        PriceAlertPayload payload = new PriceAlertPayload(
                7L, null, "BTC", AlertDirection.ABOVE,
                BigDecimal.valueOf(100), BigDecimal.valueOf(105), null, null, "TRY");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.emailModel()).containsEntry("assetCode", "BTC");
        assertThat(result.title()).isEqualTo("BTC alarmı tetiklendi");
    }

    @Test
    void render_formatsThresholdAsPercent_whenDirectionIsPercentBased() {
        PriceAlertPayload payload = new PriceAlertPayload(
                7L, MarketType.CRYPTO, "BTC", AlertDirection.CHANGE_PCT_UP,
                BigDecimal.valueOf(5), BigDecimal.valueOf(108), null, null, "TRY");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat((String) result.emailModel().get("thresholdFormatted")).startsWith("%");
        assertThat(result.emailModel()).containsEntry("isPercent", true);
        assertThat(result.emailModel()).containsEntry("changePercent", null);
    }

    @Test
    void render_usesQuoteCurrencySymbol_forUsdNativeViop() {
        PriceAlertPayload payload = new PriceAlertPayload(
                7L, MarketType.VIOP, "F_XU0300625", AlertDirection.ABOVE,
                BigDecimal.valueOf(12.5), BigDecimal.valueOf(13.0), null, null, "USD");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        // A USD-quoted VIOP contract's price/threshold must render with "$", not the hardcoded "₺".
        assertThat((String) result.emailModel().get("priceFormatted")).startsWith("$");
        assertThat((String) result.emailModel().get("thresholdFormatted")).startsWith("$");
        assertThat(result.body()).contains("$");
    }

    @Test
    void render_usesEuroSymbol_whenQuoteCurrencyIsEur() {
        PriceAlertPayload payload = new PriceAlertPayload(
                7L, MarketType.VIOP, "F_XU0300625", AlertDirection.ABOVE,
                BigDecimal.valueOf(12.5), BigDecimal.valueOf(13.0), null, null, "EUR");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat((String) result.emailModel().get("priceFormatted")).startsWith("€");
        assertThat((String) result.emailModel().get("thresholdFormatted")).startsWith("€");
    }
}
