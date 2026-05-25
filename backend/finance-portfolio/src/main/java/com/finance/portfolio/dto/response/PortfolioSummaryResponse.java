package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.util.Map;

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
