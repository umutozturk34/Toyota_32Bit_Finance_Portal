package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Aggregated view of all lots of one asset in a portfolio: open quantity, weighted-average entry, current
 * value and total PnL in TRY, plus per-currency (USD/EUR) entry-FX frames — the same {@link CurrencyFramePct}
 * map {@code PortfolioSummaryResponse.frames} carries. Without the frames the detail page rendered a holding's
 * TRY return (e.g. a USD lot at +6598%, the lira devaluation) beside a "$" label; the frame lets it show the
 * asset's own-currency return instead (~0% for a 12-USD holding worth 12 USD).
 */
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
        BigDecimal pnlPercent,
        Map<String, CurrencyFramePct> frames
) { }
