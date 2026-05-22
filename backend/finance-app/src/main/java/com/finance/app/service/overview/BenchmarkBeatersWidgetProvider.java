package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.response.InflationBeaterEntry;
import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.app.analytics.service.InflationBeaterService;
import com.finance.app.dto.response.overview.BenchmarkBeatersData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
public class BenchmarkBeatersWidgetProvider implements OverviewWidgetProvider {

    private static final String DEFAULT_BENCHMARK_CODE = "TP.TUFE1YI.T1";
    private static final String DEFAULT_PERIOD = "1Y";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MIN_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final String ASSET_TYPE_ALL = "ALL";

    private final InflationBeaterService inflationBeaterService;

    @Override
    public WidgetKind kind() {
        return WidgetKind.BENCHMARK_BEATERS;
    }

    @Override
    public BenchmarkBeatersData fetch(String userSub, WidgetSection section) {
        String benchmarkCode = readBenchmarkCode(section);
        String assetTypeFilter = readAssetType(section);
        int limit = readLimit(section);
        String period = readPeriod(section);

        InflationBeaterResponse response;
        try {
            response = inflationBeaterService.rank(period, benchmarkCode);
        } catch (RuntimeException ex) {
            log.warn("BenchmarkBeatersWidget rank failed benchmark={} period={}: {}",
                    benchmarkCode, period, ex.getMessage());
            return new BenchmarkBeatersData(benchmarkCode, BigDecimal.ZERO, period, List.of());
        }

        List<BenchmarkBeatersData.BeaterRow> rows = filterAndLimit(response.entries(), assetTypeFilter, limit);
        return new BenchmarkBeatersData(
                response.benchmarkCode(),
                response.benchmarkReturnPct() != null ? response.benchmarkReturnPct() : BigDecimal.ZERO,
                period,
                rows);
    }

    private List<BenchmarkBeatersData.BeaterRow> filterAndLimit(List<InflationBeaterEntry> entries,
                                                                String filter,
                                                                int limit) {
        List<BenchmarkBeatersData.BeaterRow> rows = new ArrayList<>(Math.min(entries.size(), limit));
        for (InflationBeaterEntry e : entries) {
            String typeStr = e.type() != null ? e.type().name() : "";
            if (filter != null && !filter.equalsIgnoreCase(typeStr)) continue;
            rows.add(new BenchmarkBeatersData.BeaterRow(
                    typeStr, e.code(), e.name(),
                    e.nominalReturnPct(), e.excessReturnPct(), e.beatsBenchmark()));
            if (rows.size() >= limit) break;
        }
        return rows;
    }

    private String readBenchmarkCode(WidgetSection section) {
        JsonNode node = section.config().get("benchmarkCode");
        if (node == null || node.isNull()) return DEFAULT_BENCHMARK_CODE;
        String value = node.asString(null);
        return (value == null || value.isBlank()) ? DEFAULT_BENCHMARK_CODE : value;
    }

    private String readPeriod(WidgetSection section) {
        JsonNode node = section.config().get("period");
        if (node == null || node.isNull()) return DEFAULT_PERIOD;
        String value = node.asString(null);
        return (value == null || value.isBlank()) ? DEFAULT_PERIOD : value;
    }

    private String readAssetType(WidgetSection section) {
        JsonNode node = section.config().get("assetType");
        if (node == null || node.isNull()) return null;
        String value = node.asString(null);
        if (value == null || value.isBlank() || ASSET_TYPE_ALL.equalsIgnoreCase(value)) return null;
        return value;
    }

    private int readLimit(WidgetSection section) {
        JsonNode node = section.config().get("limit");
        if (node == null || !node.isInt()) return DEFAULT_LIMIT;
        int requested = node.asInt();
        if (requested < MIN_LIMIT) return MIN_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }
}
