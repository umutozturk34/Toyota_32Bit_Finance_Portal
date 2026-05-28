package com.finance.app.analytics.dto.response;

import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Scenario result: the invested amount, window, CPI growth over it, target currency, and per-instrument series. */
public record ScenarioResponse(
        BigDecimal amount,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal cpiGrowthPct,
        Currency targetCurrency,
        List<ScenarioSeries> series) {
}
