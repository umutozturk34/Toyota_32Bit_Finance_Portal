package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public record PortfolioUpdatedPayload(
        BigDecimal totalValue,
        BigDecimal dailyPnl,
        BigDecimal dailyPnlPercent,
        int portfolioCount,
        String source
) implements NotificationPayload {

    @Override
    public NotificationType type() {
        return NotificationType.PORTFOLIO_UPDATED;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        if (totalValue != null) metadata.put("totalValue", totalValue);
        if (dailyPnl != null) metadata.put("dailyPnl", dailyPnl);
        if (dailyPnlPercent != null) metadata.put("dailyPnlPercent", dailyPnlPercent);
        metadata.put("portfolioCount", portfolioCount);
        return metadata;
    }
}
