package com.finance.notification.core.dispatch.payload;

import com.finance.common.model.MarketType;
import com.finance.notification.core.model.NotificationType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public record WatchlistDeltaPayload(
        Long watchlistItemId,
        MarketType marketType,
        String assetCode,
        BigDecimal lastSeenPrice,
        BigDecimal currentPrice,
        BigDecimal deltaPercent,
        String image,
        String assetName
) implements NotificationPayload {

    @Override
    public NotificationType type() {
        return NotificationType.WATCHLIST_DELTA;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("watchlistItemId", watchlistItemId);
        metadata.put("marketType", marketType.name());
        metadata.put("assetCode", assetCode);
        metadata.put("lastSeenPrice", lastSeenPrice);
        metadata.put("currentPrice", currentPrice);
        metadata.put("deltaPercent", deltaPercent);
        if (image != null) metadata.put("image", image);
        if (assetName != null) metadata.put("assetName", assetName);
        return metadata;
    }
}
