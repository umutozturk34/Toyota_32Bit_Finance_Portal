package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * An instrument's native-currency price/rate history plus the FX context to express it in a target
 * currency: {@code baseFx} is the rate at the first observation and {@code fxByDate} the per-date rate.
 * Keeping price and FX separate lets the scenario engine combine the two effects per day.
 */
public record PricedSeries(
        List<HistoryPoint> rawPoints,
        Currency nativeCurrency,
        Currency targetCurrency,
        BigDecimal baseFx,
        Map<LocalDate, BigDecimal> fxByDate
) {
    public boolean isEmpty() {
        return rawPoints == null || rawPoints.isEmpty();
    }

    /** FX factor recorded for {@code date}, or null if none (caller should skip that point). */
    public BigDecimal fxAt(LocalDate date) {
        return fxByDate != null ? fxByDate.get(date) : null;
    }
}
