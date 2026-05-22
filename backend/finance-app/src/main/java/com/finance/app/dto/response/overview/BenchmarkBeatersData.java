package com.finance.app.dto.response.overview;

import java.math.BigDecimal;
import java.util.List;

public record BenchmarkBeatersData(
        String benchmarkCode,
        BigDecimal benchmarkReturnPct,
        String period,
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
