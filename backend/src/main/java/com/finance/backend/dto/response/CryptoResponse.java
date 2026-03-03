package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CryptoResponse(
        String id,
        String symbol,
        String name,
        BigDecimal currentPrice,
        BigDecimal currentPriceTry,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        BigDecimal marketCap,
        BigDecimal totalVolume,
        LocalDateTime lastUpdated
) {}
