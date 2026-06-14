package com.finance.app.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One observation in an analytics series: a date and its value (price, rate, or portfolio value).
 * {@code pnlByCcy} (nullable, USD/EUR) carries the portfolio P&L per display currency for the compare
 * money overlay, so the foreign-currency figure is the entry-FX cost-based P&L (value@point-date FX −
 * cost@entry-date FX) instead of a single-rate conversion of the netted TRY P&L. {@code returnIndexByCcy}
 * (nullable, USD/EUR) carries the per-currency cumulative return index (100 + 100×pnl/|cost|) so the compare
 * portfolio LINE plots the real foreign-currency return instead of FX-converting the netted TRY index at one date.
 */
public record HistoryPoint(LocalDate date, BigDecimal value, Map<String, BigDecimal> pnlByCcy,
                           Map<String, BigDecimal> returnIndexByCcy) {
    public HistoryPoint(LocalDate date, BigDecimal value) {
        this(date, value, null, null);
    }

    public HistoryPoint(LocalDate date, BigDecimal value, Map<String, BigDecimal> pnlByCcy) {
        this(date, value, pnlByCcy, null);
    }
}
