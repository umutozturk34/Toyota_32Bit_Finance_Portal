package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockResponse(
        String symbol,
        String name,
        String exchange,
        BigDecimal currentPrice,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        Long volume,
        BigDecimal priceChangePercent,
        BigDecimal priceChangeAmount,
        LocalDateTime lastUpdated
) {}
