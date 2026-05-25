package com.finance.app.analytics.dto.response;

import com.finance.app.analytics.dto.AnalyticsInstrumentType;

import java.math.BigDecimal;

public record InflationBeaterEntry(
        AnalyticsInstrumentType type,
        String code,
        String name,
        BigDecimal nominalReturnPct,
        BigDecimal excessReturnPct,
        boolean beatsBenchmark) {
}
