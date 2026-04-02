package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PerformancePoint(
        LocalDateTime timestamp,
        BigDecimal totalValueTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent
) {}
