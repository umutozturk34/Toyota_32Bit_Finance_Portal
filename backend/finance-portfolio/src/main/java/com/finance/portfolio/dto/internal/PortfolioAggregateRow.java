package com.finance.portfolio.dto.internal;

import com.finance.shared.util.PercentChangeCalculator;
import com.finance.portfolio.model.MoneyScale;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioAggregateRow(
        LocalDateTime createdAt,
        BigDecimal totalValueTry,
        BigDecimal cashTry,
        BigDecimal totalCostTry,
        BigDecimal totalPnlTry
) {
    public BigDecimal pnlPercent() {
        PercentChangeCalculator.Result r = PercentChangeCalculator.compute(totalValueTry, totalCostTry, MoneyScale.PRICE);
        return r.percent() != null ? r.percent() : BigDecimal.ZERO;
    }
}
