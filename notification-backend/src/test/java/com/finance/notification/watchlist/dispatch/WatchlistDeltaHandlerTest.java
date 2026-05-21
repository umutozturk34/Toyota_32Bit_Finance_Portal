package com.finance.notification.watchlist.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.common.model.MarketType;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.WatchlistDeltaPayload;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.testsupport.HandlerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistDeltaHandlerTest {

    private static final NotificationDispatchProperties DISPATCH = new NotificationDispatchProperties(
            new NotificationDispatchProperties.Formatting(2, 6, 6),
            new NotificationDispatchProperties.WatchlistDelta(3),
            new NotificationDispatchProperties.Fanout(200),
            null);

    private WatchlistDeltaHandler handler;

    @BeforeEach
    void setUp() {
        Translator translator = HandlerTestSupport.turkishTranslator();
        handler = new WatchlistDeltaHandler(DISPATCH, translator);
    }

    @AfterEach
    void tearDown() {
        HandlerTestSupport.resetLocale();
    }

    private static WatchlistDeltaPayload.DeltaItem item(String code, String name, String image,
                                                         BigDecimal current, BigDecimal delta) {
        return new WatchlistDeltaPayload.DeltaItem(
                1L, code, name, image,
                BigDecimal.valueOf(100), current, delta);
    }

    @Test
    void type_returnsWatchlistDelta() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.WATCHLIST_DELTA);
    }

    @Test
    void render_singleItem_titleUsesAssetName() {
        WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                7L, "Favorilerim", false, MarketType.CRYPTO,
                List.of(item("btc", "Bitcoin", "https://x/y.png",
                        BigDecimal.valueOf(106), BigDecimal.valueOf(6))));

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Favorilerim — Bitcoin");
        assertThat(result.emailTemplate()).isEqualTo("watchlist-delta");
        assertThat(result.emailModel()).containsEntry("itemCount", 1);
        assertThat(result.emailModel()).containsEntry("watchlistName", "Favorilerim");
    }

    @Test
    void render_multipleItems_titleAggregates() {
        WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                7L, "Favorilerim", false, MarketType.CRYPTO,
                List.of(
                        item("btc", "Bitcoin", null, BigDecimal.valueOf(106), BigDecimal.valueOf(6)),
                        item("eth", "Ethereum", null, BigDecimal.valueOf(94), BigDecimal.valueOf(-6))
                ));

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Favorilerim — 2 varlıkta hareket");
        assertThat(result.body()).contains("Bitcoin").contains("Ethereum");
    }

    @Test
    void render_formatsZeroDeltaWithoutScientificNotation() {
        WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                7L, "Liste", false, MarketType.CRYPTO,
                List.of(item("btc", "Bitcoin", null,
                        BigDecimal.valueOf(100),
                        new BigDecimal("0E-8"))));

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> items = (List<java.util.Map<String, Object>>) result.emailModel().get("items");
        assertThat((String) items.get(0).get("deltaFormatted")).isEqualTo("0,00%");
    }

    @Test
    void render_fallsBackToWatchlistDefaultLabelWhenNameMissing() {
        WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                null, null, false, MarketType.CRYPTO,
                List.of(item("btc", "Bitcoin", null,
                        BigDecimal.valueOf(106), BigDecimal.valueOf(6))));

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Takip listeniz — Bitcoin");
        assertThat(result.emailModel()).containsEntry("watchlistName", "Takip listeniz");
    }

    @Test
    void render_useTranslatedLabel_whenDefaultListFlagged() {
        WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                1L, "Favoriler", true, MarketType.CRYPTO,
                List.of(item("btc", "Bitcoin", null,
                        BigDecimal.valueOf(106), BigDecimal.valueOf(6))));

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Favoriler — Bitcoin");
        assertThat(result.emailModel()).containsEntry("watchlistName", "Favoriler");
    }
}
