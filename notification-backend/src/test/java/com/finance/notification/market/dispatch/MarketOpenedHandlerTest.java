package com.finance.notification.market.dispatch;

import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketClosedPayload;
import com.finance.notification.core.dispatch.payload.MarketOpenedPayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketOpenedHandlerTest {

    private final MarketOpenedHandler handler = new MarketOpenedHandler();

    @Test
    void should_returnMarketOpenedType_when_typeQueried() {
        assertThat(handler.type()).isEqualTo(NotificationType.MARKET_OPENED);
    }

    @Test
    void should_renderTurkishOpeningTitleAndBody_when_payloadHasDisplayLabel() {
        NotificationRequest request = NotificationRequest.of(
                "user-1", new MarketOpenedPayload("STOCK", "Hisse"));

        RenderedNotification rendered = handler.render(request);

        assertThat(rendered.title()).isEqualTo("Hisse piyasası açıldı");
        assertThat(rendered.body()).isEqualTo("Hisse piyasası açıldı, açılış fiyatları yüklendi.");
        assertThat(rendered.emailSubject()).isEqualTo("Finance Portal — Hisse piyasası açıldı");
        assertThat(rendered.emailTemplate()).isEqualTo("market-opened");
        assertThat(rendered.emailModel()).containsEntry("market", "STOCK")
                .containsEntry("displayLabel", "Hisse");
    }

    @Test
    void should_fallbackToMarketCode_when_displayLabelIsNull() {
        NotificationRequest request = NotificationRequest.of(
                "user-1", new MarketOpenedPayload("STOCK", null));

        RenderedNotification rendered = handler.render(request);

        assertThat(rendered.title()).isEqualTo("STOCK piyasası açıldı");
        assertThat(rendered.emailModel()).containsEntry("displayLabel", "STOCK");
    }

    @Test
    void should_throwIllegalArgument_when_payloadTypeMismatched() {
        NotificationRequest request = NotificationRequest.of(
                "user-1", new MarketClosedPayload("STOCK", "Hisse"));

        assertThatThrownBy(() -> handler.render(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MarketOpenedHandler expects MarketOpenedPayload");
    }
}
