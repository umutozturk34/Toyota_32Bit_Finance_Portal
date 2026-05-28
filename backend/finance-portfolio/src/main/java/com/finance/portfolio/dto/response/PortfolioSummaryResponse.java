package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/** Headline portfolio figures in TRY: total value/cost/PnL, daily PnL, inflation-adjusted (real) return, and per-currency frames. */
public record PortfolioSummaryResponse(
        BigDecimal totalValueTry,
        BigDecimal totalEntryValueTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        BigDecimal dailyPnlTry,
        BigDecimal dailyPnlPercent,
        BigDecimal realPnlTry,
        BigDecimal realPnlPercent,
        BigDecimal cpiGrowthPercent,
        Map<String, CurrencyFramePct> frames
) {}
