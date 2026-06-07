package com.finance.app.analytics.dto.response;

import com.finance.app.analytics.dto.AnalyticsInstrumentType;

import java.util.Map;

/** One asset with its per-window realized returns, keyed by period token (1W..5Y). */
public record AssetReturnRow(
        AnalyticsInstrumentType type,
        String code,
        String name,
        Map<String, PeriodReturn> periods) {
}
