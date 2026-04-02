package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssetSeriesPoint(
        LocalDateTime timestamp,
        BigDecimal unitPriceTry,
        BigDecimal marketValueTry,
        BigDecimal pnlTry
) {}
