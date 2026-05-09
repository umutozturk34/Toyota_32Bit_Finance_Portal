package com.finance.notification.market.dispatch;

import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketClosedPayload;
import com.finance.notification.core.dispatch.payload.MarketOpenedPayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketClosedHandlerTest {

    private final MarketClosedHandler handler = new MarketClosedHandler();

    @Test
    void should_returnMarketClosedType_when_typeQueried() {
        NotificationType type = handler.type();

        assertThat(type).isEqualTo(NotificationType.MARKET_CLOSED);
    }

    @Test
    void should_renderTurkishClosingTitleAndBody_when_payloadHasDisplayLabel() {
        NotificationRequest request = NotificationRequest.of(
                "user-1", new MarketClosedPayload("FOREX", "Döviz"));

        RenderedNotification rendered = handler.render(request);

        assertThat(rendered.title()).isEqualTo("Döviz piyasası kapandı");
        assertThat(rendered.body()).isEqualTo("Döviz seansı sona erdi, kapanış fiyatları yüklendi.");
        assertThat(rendered.emailSubject()).isEqualTo("Finance Portal — Döviz piyasası kapandı");
        assertThat(rendered.emailTemplate()).isEqualTo("market-closed");
        assertThat(rendered.emailModel()).containsEntry("market", "FOREX")
                .containsEntry("displayLabel", "Döviz");
    }

    @Test
    void should_fallbackToMarketCode_when_displayLabelIsNull() {
        NotificationRequest request = NotificationRequest.of(
                "user-1", new MarketClosedPayload("BOND", null));

        RenderedNotification rendered = handler.render(request);

        assertThat(rendered.title()).isEqualTo("BOND piyasası kapandı");
        assertThat(rendered.emailModel()).containsEntry("displayLabel", "BOND");
    }

    @Test
    void should_throwIllegalArgument_when_payloadTypeMismatched() {
        NotificationRequest request = NotificationRequest.of(
                "user-1", new MarketOpenedPayload("FOREX", "Döviz"));

        assertThatThrownBy(() -> handler.render(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MarketClosedHandler expects MarketClosedPayload");
    }
}
