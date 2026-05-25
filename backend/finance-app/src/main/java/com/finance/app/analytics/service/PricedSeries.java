package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    public BigDecimal fxAt(LocalDate date) {
        return fxByDate != null ? fxByDate.get(date) : null;
    }
}
