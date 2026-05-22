package com.finance.app.analytics.dto.response;

import com.finance.app.analytics.dto.AnalyticsInstrument;

import java.math.BigDecimal;
import java.util.List;

public record ScenarioSeries(
        AnalyticsInstrument instrument,
        List<ScenarioPoint> points,
        BigDecimal finalValue,
        BigDecimal nominalReturnPct,
        BigDecimal realReturnPct,
        boolean partial) {
}
