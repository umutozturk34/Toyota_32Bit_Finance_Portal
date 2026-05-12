package com.finance.notification.core.dispatch.payload;

import com.finance.common.model.MarketType;
import com.finance.notification.core.model.NotificationType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record WatchlistDeltaPayload(
        Long watchlistId,
        String watchlistName,
        boolean defaultList,
        MarketType marketType,
        List<DeltaItem> items
) implements NotificationPayload {

    public record DeltaItem(
            Long watchlistItemId,
            String assetCode,
            String assetName,
            String image,
            BigDecimal lastSeenPrice,
            BigDecimal currentPrice,
            BigDecimal deltaPercent
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("watchlistItemId", watchlistItemId);
            m.put("assetCode", assetCode);
            if (assetName != null) m.put("assetName", assetName);
            if (image != null) m.put("image", image);
            m.put("lastSeenPrice", lastSeenPrice);
            m.put("currentPrice", currentPrice);
            m.put("deltaPercent", deltaPercent);
            return m;
        }
    }

    @Override
    public NotificationType type() {
        return NotificationType.WATCHLIST_DELTA;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("watchlistId", watchlistId);
        if (watchlistName != null) metadata.put("watchlistName", watchlistName);
        metadata.put("defaultList", defaultList);
        metadata.put("marketType", marketType.name());
        metadata.put("items", items.stream().map(DeltaItem::toMap).toList());
        return metadata;
    }
}
