package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** One point on a portfolio performance series: total value/cash/PnL in TRY at a timestamp, with per-asset details and trade events. */
public record PerformancePoint(
        LocalDateTime timestamp,
        BigDecimal totalValueTry,
        BigDecimal cashTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        List<PerformanceAssetDetail> details,
        List<PerformanceEvent> events
) {}
