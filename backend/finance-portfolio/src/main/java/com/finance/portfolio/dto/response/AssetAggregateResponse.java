package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Aggregated view of all lots of one asset in a portfolio: open quantity, weighted-average entry, current value and total PnL in TRY. */
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
