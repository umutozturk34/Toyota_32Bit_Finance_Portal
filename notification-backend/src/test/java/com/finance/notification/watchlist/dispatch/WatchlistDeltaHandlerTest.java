package com.finance.notification.watchlist.dispatch;

import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.WatchlistDeltaPayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistDeltaHandlerTest {

    private final WatchlistDeltaHandler handler = new WatchlistDeltaHandler();

    @Test
    void type_returnsWatchlistDelta() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.WATCHLIST_DELTA);
    }

    @Test
    void render_buildsTitleBodyAndEmailModelFromPayload() {
        WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                12L, MarketType.CRYPTO, "BTC",
                BigDecimal.valueOf(100), BigDecimal.valueOf(105), BigDecimal.valueOf(5),
                null, null);

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("BTC takip listesi hareketi");
        assertThat(result.emailTemplate()).isEqualTo("watchlist-delta");
        assertThat(result.emailModel()).containsEntry("assetCode", "BTC");
        assertThat(result.emailModel()).containsEntry("isUp", true);
    }

    @Test
    void render_usesAssetNameWhenProvided() {
        WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                12L, MarketType.CRYPTO, "btc",
                BigDecimal.valueOf(100), BigDecimal.valueOf(80), BigDecimal.valueOf(-20),
                "https://x/y.png", "Bitcoin");

        RenderedNotification result = handler.render(NotificationRequest.of("u", payload));

        assertThat(result.title()).isEqualTo("Bitcoin takip listesi hareketi");
        assertThat(result.emailModel()).containsEntry("assetName", "Bitcoin");
        assertThat(result.emailModel()).containsEntry("isUp", false);
    }
}
