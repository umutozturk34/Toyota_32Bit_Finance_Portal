package com.finance.portfolio.dto.internal;

import com.finance.shared.util.PercentChangeCalculator;
import com.finance.portfolio.model.MoneyScale;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Lightweight JPA projection of a daily snapshot row used to build performance series without loading full entities. */
public record PortfolioAggregateRow(
        LocalDateTime createdAt,
        BigDecimal totalValueTry,
        BigDecimal cashTry,
        BigDecimal totalCostTry,
        BigDecimal totalPnlTry
) {
    /** PnL percent derived on the fly from total value vs. cost; zero when cost is non-positive. */
    public BigDecimal pnlPercent() {
        PercentChangeCalculator.Result r = PercentChangeCalculator.compute(totalValueTry, totalCostTry, MoneyScale.PRICE);
        return r.percent() != null ? r.percent() : BigDecimal.ZERO;
    }
}
