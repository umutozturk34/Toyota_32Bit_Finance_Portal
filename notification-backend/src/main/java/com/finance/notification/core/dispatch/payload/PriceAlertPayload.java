package com.finance.notification.core.dispatch.payload;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.core.model.NotificationType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public record PriceAlertPayload(
        Long alertId,
        MarketType marketType,
        String assetCode,
        AlertDirection direction,
        BigDecimal threshold,
        BigDecimal currentPrice,
        String image,
        String assetName
) implements NotificationPayload {

    @Override
    public NotificationType type() {
        return NotificationType.PRICE_ALERT_FIRED;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("alertId", alertId);
        metadata.put("marketType", marketType.name());
        metadata.put("assetCode", assetCode);
        metadata.put("direction", direction.name());
        metadata.put("threshold", threshold);
        metadata.put("currentPrice", currentPrice);
        if (image != null) metadata.put("image", image);
        if (assetName != null) metadata.put("assetName", assetName);
        return metadata;
    }
}
