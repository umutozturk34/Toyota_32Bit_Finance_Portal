package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;

import java.util.HashMap;
import java.util.Map;

/** Payload signalling that a market's snapshot data was refreshed. */
public record MarketDataUpdatedPayload(String market, String source) implements NotificationPayload {

    @Override
    public NotificationType type() {
        return NotificationType.MARKET_DATA_UPDATED;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("market", market);
        if (source != null) metadata.put("source", source);
        return metadata;
    }
}
