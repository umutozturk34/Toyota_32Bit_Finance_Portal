package com.finance.app.dto.response.overview;

import java.math.BigDecimal;
import java.util.List;

/** BENCHMARK_BEATERS widget payload: the benchmark, period, comparison currency, and the ranked rows. */
public record BenchmarkBeatersData(
        String benchmarkCode,
        String benchmarkLabel,
        BigDecimal benchmarkReturnPct,
        String period,
        String comparisonCurrency,
        List<BeaterRow> entries
) implements WidgetData {

    @Override
    public WidgetKind kind() {
        return WidgetKind.BENCHMARK_BEATERS;
    }

    public record BeaterRow(
            String type,
            String code,
            String name,
            BigDecimal nominalReturnPct,
            BigDecimal excessReturnPct,
            boolean beatsBenchmark
    ) {
    }
}
