package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AssetSeriesPoint(
        LocalDateTime timestamp,
        BigDecimal unitPriceTry,
        BigDecimal marketValueTry,
        BigDecimal pnlTry,
        BigDecimal dailyPnlTry,
        BigDecimal dailyPnlPercent,
        List<PerformanceEvent> events
) {
    public AssetSeriesPoint withEvents(List<PerformanceEvent> newEvents) {
        return new AssetSeriesPoint(timestamp, unitPriceTry, marketValueTry,
                pnlTry, dailyPnlTry, dailyPnlPercent, newEvents);
    }
}
