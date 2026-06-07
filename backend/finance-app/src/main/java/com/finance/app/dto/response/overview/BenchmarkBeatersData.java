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

    /**
     * One candidate measured against the benchmark: its type/code/name, its own nominal return, the
     * excess over the benchmark (nominal minus benchmark return), and whether that excess is positive.
     */
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
