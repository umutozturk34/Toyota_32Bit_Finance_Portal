package com.finance.notification.market.dispatch;

import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketDataUpdatedPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataUpdatedHandlerTest {

    private final MarketDataUpdatedHandler handler = new MarketDataUpdatedHandler();

    private NotificationRequest requestWithSource(String source) {
        return NotificationRequest.of("user-1", new MarketDataUpdatedPayload("STOCK", "Hisse", source));
    }

    @Test
    void should_renderSabahTitle_when_sourceContainsMorning() {
        RenderedNotification rendered = handler.render(requestWithSource("scheduled-stock-morning"));

        assertThat(rendered.title()).isEqualTo("Hisse · Sabah güncellemesi");
        assertThat(rendered.body()).contains("sabah güncellemesi");
    }

    @Test
    void should_renderOglenTitle_when_sourceContainsAfternoonOrMidday() {
        RenderedNotification afternoon = handler.render(requestWithSource("scheduled-stock-afternoon"));
        RenderedNotification midday = handler.render(requestWithSource("scheduled-news-midday"));

        assertThat(afternoon.title()).isEqualTo("Hisse · Öğlen güncellemesi");
        assertThat(midday.title()).isEqualTo("Hisse · Öğlen güncellemesi");
    }

    @Test
    void should_renderAksamTitle_when_sourceContainsEvening() {
        RenderedNotification rendered = handler.render(requestWithSource("scheduled-stock-evening"));

        assertThat(rendered.title()).isEqualTo("Hisse · Akşam güncellemesi");
    }

    @Test
    void should_renderGunlukTitle_when_sourceContainsDailyOrFull() {
        RenderedNotification daily = handler.render(requestWithSource("scheduled-bond-daily"));
        RenderedNotification full = handler.render(requestWithSource("scheduled-fund-full"));

        assertThat(daily.title()).isEqualTo("Hisse · Günlük güncellemesi");
        assertThat(full.title()).isEqualTo("Hisse · Günlük güncellemesi");
    }

    @Test
    void should_renderGenericTitle_when_sourceUnknownOrNull() {
        RenderedNotification adminSource = handler.render(requestWithSource("admin"));
        RenderedNotification nullSource = handler.render(requestWithSource(null));

        assertThat(adminSource.title()).isEqualTo("Hisse verileri güncellendi");
        assertThat(nullSource.title()).isEqualTo("Hisse verileri güncellendi");
    }
}
