package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payload reporting a user's refreshed portfolio totals plus a per-portfolio breakdown so the summary can list
 * each portfolio by name with its own status (value + daily P/L) instead of one merged aggregate.
 */
public record PortfolioUpdatedPayload(
        BigDecimal totalValue,
        BigDecimal dailyPnl,
        BigDecimal dailyPnlPercent,
        int portfolioCount,
        List<Line> portfolios,
        String source
) implements NotificationPayload {

    /** One portfolio's status in the summary: its name and its own value + daily P/L. */
    public record Line(Long id, String name, BigDecimal totalValue, BigDecimal dailyPnl, BigDecimal dailyPnlPercent) {}

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
        // Per-portfolio rows so the in-app panel can render each portfolio's name + status (theme-aware on the
        // client). Stored as plain maps so they serialize cleanly into the notification's JSONB metadata.
        if (portfolios != null && !portfolios.isEmpty()) {
            List<Map<String, Object>> rows = new ArrayList<>(portfolios.size());
            for (Line line : portfolios) {
                Map<String, Object> row = new HashMap<>();
                if (line.id() != null) row.put("id", line.id());
                row.put("name", line.name());
                if (line.totalValue() != null) row.put("totalValue", line.totalValue());
                if (line.dailyPnl() != null) row.put("dailyPnl", line.dailyPnl());
                if (line.dailyPnlPercent() != null) row.put("dailyPnlPercent", line.dailyPnlPercent());
                rows.add(row);
            }
            metadata.put("portfolios", rows);
        }
        return metadata;
    }
}
