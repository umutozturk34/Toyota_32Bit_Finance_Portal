package com.finance.app.analytics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ScenarioResponse(
        BigDecimal amount,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal cpiGrowthPct,
        List<ScenarioSeries> series) {
}
