package com.finance.notification.watchlist.dispatch;

import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WatchlistDeltaHandler implements NotificationHandler {

    @Override
    public NotificationType type() {
        return NotificationType.WATCHLIST_DELTA;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        Map<String, Object> data = request.data();
        String assetCode = String.valueOf(data.getOrDefault("assetCode", "?"));
        Object deltaPercent = data.getOrDefault("deltaPercent", "?");
        Object currentPrice = data.getOrDefault("currentPrice", "?");

        String title = "Takip listesi: " + assetCode;
        String body = String.format("%s anlık %s — %% değişim %s",
                assetCode, currentPrice, deltaPercent);

        return new RenderedNotification(
                title,
                body,
                "Finance Portal — takip listesi hareketi",
                "watchlist-delta",
                Map.of(
                        "assetCode", assetCode,
                        "currentPrice", currentPrice,
                        "deltaPercent", deltaPercent
                ));
    }
}
