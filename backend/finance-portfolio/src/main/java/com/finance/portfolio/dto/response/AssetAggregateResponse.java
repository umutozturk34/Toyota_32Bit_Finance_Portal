package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssetAggregateResponse(
        String assetType,
        String assetCode,
        String assetName,
        String assetImage,
        int lotCount,
        BigDecimal totalQuantity,
        LocalDateTime earliestEntryDate,
        BigDecimal weightedAvgEntryPrice,
        BigDecimal currentPriceTry,
        BigDecimal totalEntryValueTry,
        BigDecimal totalMarketValueTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent
) { }
