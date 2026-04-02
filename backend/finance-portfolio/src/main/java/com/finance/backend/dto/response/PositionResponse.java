package com.finance.backend.dto.response;

import java.math.BigDecimal;

public record PositionResponse(
        Long id,
        String assetType,
        String assetCode,
        BigDecimal quantity,
        BigDecimal averageCostTry,
        BigDecimal totalCostTry,
        BigDecimal currentPriceTry,
        BigDecimal marketValueTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent
) {}
