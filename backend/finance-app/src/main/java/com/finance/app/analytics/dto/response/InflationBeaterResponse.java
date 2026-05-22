package com.finance.app.analytics.dto.response;

import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
