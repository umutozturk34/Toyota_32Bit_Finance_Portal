package com.finance.backend.dto.response;

import java.math.BigDecimal;

public record PortfolioSummaryResponse(
        BigDecimal totalValueTry,
        BigDecimal totalCostTry,
        BigDecimal cashBalanceTry,
        BigDecimal unrealizedPnlTry,
        BigDecimal realizedPnlTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent
) {}
