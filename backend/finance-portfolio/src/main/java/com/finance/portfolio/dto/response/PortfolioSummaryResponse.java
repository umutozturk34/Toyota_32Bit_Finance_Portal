package com.finance.portfolio.dto.response;

import java.math.BigDecimal;

public record PortfolioSummaryResponse(
        BigDecimal totalValueTry,
        BigDecimal totalEntryValueTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        BigDecimal dailyPnlTry,
        BigDecimal dailyPnlPercent
) {}
