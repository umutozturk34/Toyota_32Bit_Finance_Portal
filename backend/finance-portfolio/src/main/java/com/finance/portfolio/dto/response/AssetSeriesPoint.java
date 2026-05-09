package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssetSeriesPoint(
        LocalDateTime timestamp,
        BigDecimal unitPriceTry,
        BigDecimal marketValueTry,
        BigDecimal pnlTry,
        BigDecimal dailyPnlTry,
        BigDecimal dailyPnlPercent
) {}
