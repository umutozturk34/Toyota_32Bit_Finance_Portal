package com.finance.app.analytics.dto.response;

import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Inflation-beater ranking: the window, benchmark and its return, the comparison currency, how many of
 * {@code totalCount} instruments beat the benchmark, and the entries sorted by excess return.
 */
public record InflationBeaterResponse(
        LocalDate startDate,
        LocalDate endDate,
        String benchmarkCode,
        String benchmarkLabel,
        BigDecimal benchmarkReturnPct,
        int beatingCount,
        int totalCount,
        Currency comparisonCurrency,
        List<InflationBeaterEntry> entries) {
}
