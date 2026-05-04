package com.finance.notification.watchlist.dispatch;

import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistDeltaHandlerTest {

    private final WatchlistDeltaHandler handler = new WatchlistDeltaHandler();

    @Test
    void type_returnsWatchlistDelta() {
        NotificationType result = handler.type();

        assertThat(result).isEqualTo(NotificationType.WATCHLIST_DELTA);
    }

    @Test
    void render_buildsTitleBodyAndEmailModelFromData() {
        NotificationRequest request = NotificationRequest.of("u",
                NotificationType.WATCHLIST_DELTA,
                Map.of(
                        "assetCode", "BTC",
                        "currentPrice", BigDecimal.valueOf(105),
                        "deltaPercent", BigDecimal.valueOf(5)
                ));

        RenderedNotification result = handler.render(request);

        assertThat(result.title()).isEqualTo("Takip listesi: BTC");
        assertThat(result.emailTemplate()).isEqualTo("watchlist-delta");
        assertThat(result.emailModel()).containsEntry("assetCode", "BTC");
        assertThat(result.emailModel()).containsEntry("deltaPercent", BigDecimal.valueOf(5));
    }
}
