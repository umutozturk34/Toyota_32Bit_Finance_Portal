package com.finance.backend.dto.response;

import java.math.BigDecimal;

public record PositionResponse(
        Long id,
        String assetType,
        String assetCode,
        String assetName,
        String assetImage,
        BigDecimal quantity,
        BigDecimal averageCostTry,
        BigDecimal totalCostTry,
        BigDecimal currentPriceTry,
        BigDecimal sellPriceTry,
        BigDecimal commissionRate,
        BigDecimal marketValueTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent
) {}
