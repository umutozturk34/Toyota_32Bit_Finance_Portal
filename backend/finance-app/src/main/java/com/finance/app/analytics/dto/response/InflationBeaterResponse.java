package com.finance.app.analytics.dto.response;

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
        List<InflationBeaterEntry> entries) {
}
