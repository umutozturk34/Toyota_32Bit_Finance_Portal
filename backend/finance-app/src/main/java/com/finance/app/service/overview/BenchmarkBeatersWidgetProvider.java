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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final String VERDICT_ALL = "ALL";
    private static final String VERDICT_WINNERS = "WINNERS";
    private static final String VERDICT_LOSERS = "LOSERS";
    private static final String SORT_DESC = "DESC";
    private static final String SORT_ASC = "ASC";

    private final InflationBeaterService inflationBeaterService;

    @Override
    public WidgetKind kind() {
        return WidgetKind.BENCHMARK_BEATERS;
    }

    @Override
    public BenchmarkBeatersData fetch(String userSub, WidgetSection section) {
        String benchmarkCode = readBenchmarkCode(section);
        Set<String> assetTypeFilter = readAssetTypes(section);
        int limit = readLimit(section);
        String period = readPeriod(section);
        String verdict = readVerdict(section);
        boolean ascending = SORT_ASC.equalsIgnoreCase(readSortDir(section));

        InflationBeaterResponse response;
        try {
            response = inflationBeaterService.peekCache(period, benchmarkCode);
            if (response == null) {
                inflationBeaterService.warmAsync(period, benchmarkCode);
                log.debug("BenchmarkBeatersWidget cold cache, async warm triggered benchmark={} period={}",
                        benchmarkCode, period);
                return new BenchmarkBeatersData(benchmarkCode, null, BigDecimal.ZERO, period, null, List.of());
            }
        } catch (RuntimeException ex) {
            log.warn("BenchmarkBeatersWidget peek failed benchmark={} period={}: {}",
                    benchmarkCode, period, ex.getMessage());
            return new BenchmarkBeatersData(benchmarkCode, null, BigDecimal.ZERO, period, null, List.of());
        }

        List<BenchmarkBeatersData.BeaterRow> rows = filterAndLimit(
                response.entries(), assetTypeFilter, verdict, ascending, limit);
        return new BenchmarkBeatersData(
                response.benchmarkCode(),
                response.benchmarkLabel(),
                response.benchmarkReturnPct() != null ? response.benchmarkReturnPct() : BigDecimal.ZERO,
                period,
                response.comparisonCurrency() != null ? response.comparisonCurrency().name() : null,
                rows);
    }

    private List<BenchmarkBeatersData.BeaterRow> filterAndLimit(List<InflationBeaterEntry> entries,
                                                                Set<String> typeFilter,
                                                                String verdict,
                                                                boolean ascending,
                                                                int limit) {
        List<InflationBeaterEntry> ordered = new ArrayList<>(entries);
        if (ascending) {
            java.util.Collections.reverse(ordered);
        }
        List<BenchmarkBeatersData.BeaterRow> rows = new ArrayList<>(Math.min(ordered.size(), limit));
        for (InflationBeaterEntry e : ordered) {
            String typeStr = e.type() != null ? e.type().name() : "";
            if (!typeFilter.isEmpty() && !typeFilter.contains(typeStr.toUpperCase())) continue;
            if (VERDICT_WINNERS.equalsIgnoreCase(verdict) && !Boolean.TRUE.equals(e.beatsBenchmark())) continue;
            if (VERDICT_LOSERS.equalsIgnoreCase(verdict) && Boolean.TRUE.equals(e.beatsBenchmark())) continue;
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

    private Set<String> readAssetTypes(WidgetSection section) {
        JsonNode node = section.config().get("assetType");
        if (node == null || node.isNull()) return Set.of();
        Set<String> out = new HashSet<>();
        if (node.isArray()) {
            node.forEach(n -> {
                String v = n.asString(null);
                if (v != null && !v.isBlank() && !ASSET_TYPE_ALL.equalsIgnoreCase(v)) {
                    out.add(v.toUpperCase());
                }
            });
        } else {
            String value = node.asString(null);
            if (value == null || value.isBlank()) return Set.of();
            out.addAll(Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank() && !ASSET_TYPE_ALL.equalsIgnoreCase(s))
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet()));
        }
        return out;
    }

    private int readLimit(WidgetSection section) {
        JsonNode node = section.config().get("limit");
        if (node == null || !node.isInt()) return DEFAULT_LIMIT;
        int requested = node.asInt();
        if (requested < MIN_LIMIT) return MIN_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }

    private String readVerdict(WidgetSection section) {
        JsonNode node = section.config().get("verdict");
        if (node == null || node.isNull()) return VERDICT_ALL;
        String value = node.asString(null);
        if (value == null || value.isBlank()) return VERDICT_ALL;
        if (VERDICT_WINNERS.equalsIgnoreCase(value)) return VERDICT_WINNERS;
        if (VERDICT_LOSERS.equalsIgnoreCase(value)) return VERDICT_LOSERS;
        return VERDICT_ALL;
    }

    private String readSortDir(WidgetSection section) {
        JsonNode node = section.config().get("sortDir");
        if (node == null || node.isNull()) return SORT_DESC;
        String value = node.asString(null);
        return SORT_ASC.equalsIgnoreCase(value) ? SORT_ASC : SORT_DESC;
    }
}
