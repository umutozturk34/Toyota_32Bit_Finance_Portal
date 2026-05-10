package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;

import java.util.HashMap;
import java.util.Map;

public record MarketOpenedPayload(String market) implements NotificationPayload {

    @Override
    public NotificationType type() {
        return NotificationType.MARKET_OPENED;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("market", market);
        return metadata;
    }
}
