package com.finance.portfolio.service;

import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.shared.util.PercentChangeCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mutable accumulator that sums a portfolio's open market value, closed exit/realized value and cost
 * basis across positions for one day, then folds them into a {@link PortfolioDailySnapshot}. Total
 * value is open market value plus closed exit value; realized proceeds are surfaced as {@code cashTry}.
 */
final class SnapshotTotals {

    BigDecimal totalMarketValue = BigDecimal.ZERO;
    BigDecimal cumulativeRealized = BigDecimal.ZERO;
    BigDecimal closedExitValue = BigDecimal.ZERO;
    BigDecimal totalEntryValue = BigDecimal.ZERO;

    void addEntry(BigDecimal v) { totalEntryValue = totalEntryValue.add(v); }
    void addMarket(BigDecimal v) { totalMarketValue = totalMarketValue.add(v); }
    /** Folds a closed position: accumulates its realized PnL and its exit value (entry + realized). */
    void addRealizedClose(BigDecimal realized, BigDecimal exitValue) {
        cumulativeRealized = cumulativeRealized.add(realized);
        closedExitValue = closedExitValue.add(exitValue);
    }

    /** Builds the day's aggregate snapshot from the accumulated totals and the supplied daily delta. */
    PortfolioDailySnapshot toAggregateSnapshot(Long portfolioId, LocalDate snapDate,
                                                LocalDateTime batchTimestamp, DailyDelta daily) {
        BigDecimal mv = totalMarketValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal realized = cumulativeRealized.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal closed = closedExitValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal entry = totalEntryValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal totalValue = mv.add(closed).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(totalValue, entry, MoneyScale.PRICE);
        BigDecimal pnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;
        BigDecimal pnlPct = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;
        return PortfolioDailySnapshot.builder()
                .portfolioId(portfolioId)
                .snapshotDate(snapDate)
                .createdAt(batchTimestamp)
                .totalValueTry(totalValue)
                .cashTry(realized)
                .totalCostTry(entry)
                .totalPnlTry(pnl)
                .pnlPercent(pnlPct)
                .dailyPnlTry(daily.amount())
                .dailyPnlPercent(daily.percent())
                .build();
    }
}
