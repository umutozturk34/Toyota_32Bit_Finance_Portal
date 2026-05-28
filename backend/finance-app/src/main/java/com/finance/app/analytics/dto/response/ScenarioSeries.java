package com.finance.app.analytics.dto.response;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.util.List;

/**
 * One instrument's simulated value path with its nominal and CPI-adjusted (real) returns.
 * {@code partial} flags a series that doesn't fully span the requested window (late start / early end),
 * so callers can exclude it from rankings.
 */
public record ScenarioSeries(
        AnalyticsInstrument instrument,
        List<ScenarioPoint> points,
        BigDecimal finalValue,
        BigDecimal nominalReturnPct,
        BigDecimal realReturnPct,
        Currency nativeCurrency,
        boolean partial) {
}
