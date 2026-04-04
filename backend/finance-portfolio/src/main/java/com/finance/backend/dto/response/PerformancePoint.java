package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PerformancePoint(
        LocalDateTime timestamp,
        BigDecimal totalValueTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        List<PerformanceAssetDetail> details,
        List<PerformanceEvent> events
) {}
