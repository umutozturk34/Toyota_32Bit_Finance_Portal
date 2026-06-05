package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** One point on a single-asset chart series: value/PnL in TRY at a timestamp plus any trade events in that interval. */
public record AssetSeriesPoint(
        LocalDateTime timestamp,
        BigDecimal unitPriceTry,
        BigDecimal marketValueTry,
        BigDecimal totalCostTry,
        BigDecimal pnlTry,
        BigDecimal dailyPnlTry,
        BigDecimal dailyPnlPercent,
        List<PerformanceEvent> events
) {
    public AssetSeriesPoint withEvents(List<PerformanceEvent> newEvents) {
        return new AssetSeriesPoint(timestamp, unitPriceTry, marketValueTry, totalCostTry,
                pnlTry, dailyPnlTry, dailyPnlPercent, newEvents);
    }
}
