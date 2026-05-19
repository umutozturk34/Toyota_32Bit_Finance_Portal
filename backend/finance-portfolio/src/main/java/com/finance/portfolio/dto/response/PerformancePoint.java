package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PerformancePoint(
        LocalDateTime timestamp,
        BigDecimal totalValueTry,
        BigDecimal cashTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        List<PerformanceAssetDetail> details,
        List<PerformanceEvent> events
) {}
