package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.response.InflationBeaterEntry;
import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.dto.response.ScenarioSeries;
import com.finance.common.config.AppProperties;
import com.finance.common.exception.BadRequestException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Service
@RequiredArgsConstructor
public class InflationBeaterService {

    private static final String DEFAULT_BENCHMARK = "TP.TUFE1YI.T1";
    private static final BigDecimal NOTIONAL_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final List<CuratedAsset> CURATED_DEPOSITS = List.of(
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.TRYTAS.MT01", "TRY 1H Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.TRYTAS.MT03", "TRY 1M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.TRYTAS.MT06", "TRY 3M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.TRYTAS.MT12", "TRY 6M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.USDTAS.MT03", "USD 1M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.USDTAS.MT06", "USD 3M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.USDTAS.MT12", "USD 6M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.EURTAS.MT06", "EUR 3M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.EURTAS.MT12", "EUR 6M Mevduat")
    );

    private static final Map<TrackedAssetType, AnalyticsInstrumentType> TYPE_MAP = Map.of(
            TrackedAssetType.STOCK,     AnalyticsInstrumentType.SPOT,
            TrackedAssetType.CRYPTO,    AnalyticsInstrumentType.CRYPTO,
            TrackedAssetType.FOREX,     AnalyticsInstrumentType.FOREX,
            TrackedAssetType.FUND,      AnalyticsInstrumentType.FUND,
            TrackedAssetType.COMMODITY, AnalyticsInstrumentType.COMMODITY
    );

    private static final Map<String, Integer> PERIOD_MONTHS = Map.of(
            "1M", 1, "6M", 6, "1Y", 12, "3Y", 36, "5Y", 60
    );

    private static final Map<MacroCategory, AnalyticsInstrumentType> CATEGORY_TO_TYPE = Map.of(
            MacroCategory.DEPOSIT, AnalyticsInstrumentType.DEPOSIT,
            MacroCategory.RATES, AnalyticsInstrumentType.MACRO,
            MacroCategory.INFLATION, AnalyticsInstrumentType.MACRO
    );

    private final ScenarioService scenarioService;
    private final UnifiedHistoryService historyService;
    private final MacroIndicatorQueryService macroQueryService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final AppProperties appProperties;

    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

    public InflationBeaterResponse rank(String period, String benchmarkCode) {
        Integer months = PERIOD_MONTHS.get(period);
        if (months == null) {
            throw new BadRequestException("error.analytics.unknownPeriod", period);
        }
        String code = (benchmarkCode != null && !benchmarkCode.isBlank())
                ? benchmarkCode : DEFAULT_BENCHMARK;
        String cacheKey = period + "|" + code;

        CachedResult cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            log.debug("Beaters cache hit key={}", cacheKey);
            return cached.response;
        }

        InflationBeaterResponse response = compute(period, code, months);
        cache.put(cacheKey, new CachedResult(response, System.currentTimeMillis() + appProperties.getCache().getInflationBeaterTtlMillis()));
        return response;
    }

    private InflationBeaterResponse compute(String period, String code, int months) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);

        MacroIndicator benchmark = macroQueryService.findByCode(code);
        boolean isIndex = benchmark.getUnit() == MacroUnit.INDEX
                || benchmark.getUnit() == MacroUnit.NUMBER;

        List<CuratedAsset> universe = buildUniverse();
        log.info("Computing beaters period={} benchmark={} universeSize={}",
                period, code, universe.size());

        List<AnalyticsInstrument> instruments = new ArrayList<>();
        for (CuratedAsset c : universe) {
            instruments.add(new AnalyticsInstrument(c.type, c.code));
        }
        if (!isIndex) {
            AnalyticsInstrumentType benchmarkType = CATEGORY_TO_TYPE.getOrDefault(
                    benchmark.getCategory(), AnalyticsInstrumentType.MACRO);
            instruments.add(new AnalyticsInstrument(benchmarkType, code));
        }

        ScenarioResponse scenario = scenarioService.simulate(
                new ScenarioRequest(NOTIONAL_AMOUNT, startDate, endDate, instruments));

        BigDecimal benchmarkReturn = isIndex
                ? computeIndexGrowthPct(code, startDate, endDate)
                : extractBenchmarkReturn(scenario, code);

        Map<String, String> nameLookup = nameMapFor(universe);
        List<InflationBeaterEntry> entries = scenario.series().stream()
                .filter(s -> !s.instrument().code().equals(code) || isIndex)
                .filter(s -> s.nominalReturnPct() != null)
                .map(s -> toEntry(s, benchmarkReturn, nameLookup))
                .sorted(Comparator.comparing(InflationBeaterEntry::realReturnPct,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int beating = (int) entries.stream().filter(InflationBeaterEntry::beatsInflation).count();
        log.info("Beaters ranked period={} benchmark={} return={} total={} beating={}",
                period, code, benchmarkReturn, entries.size(), beating);
        return new InflationBeaterResponse(
                startDate, endDate, code, benchmark.getLabel(),
                benchmarkReturn, beating, entries.size(), entries);
    }

    private List<CuratedAsset> buildUniverse() {
        List<CuratedAsset> universe = new ArrayList<>();
        for (var entry : TYPE_MAP.entrySet()) {
            TrackedAssetType trackedType = entry.getKey();
            AnalyticsInstrumentType analyticsType = entry.getValue();
            try {
                List<String> codes = trackedAssetQueryService.getCodes(trackedType);
                Map<String, String> names = trackedAssetQueryService.getDisplayNameMap(trackedType);
                for (String c : codes) {
                    universe.add(new CuratedAsset(analyticsType, c, names.getOrDefault(c, c)));
                }
            } catch (Exception e) {
                log.warn("Failed to enumerate tracked type={}: {}", trackedType, e.getMessage());
            }
        }
        universe.addAll(CURATED_DEPOSITS);
        return universe;
    }

    private Map<String, String> nameMapFor(List<CuratedAsset> universe) {
        Map<String, String> out = new LinkedHashMap<>();
        for (CuratedAsset c : universe) {
            out.put(c.type + "|" + c.code, c.name);
        }
        return out;
    }

    private BigDecimal computeIndexGrowthPct(String code, LocalDate start, LocalDate end) {
        List<HistoryPoint> points = historyService.getMacroSeries(code,
                start.minusMonths(2), end.plusMonths(1));
        if (points.size() < 2) return BigDecimal.ZERO;
        BigDecimal first = points.get(0).value();
        BigDecimal last = points.get(points.size() - 1).value();
        if (first == null || first.signum() <= 0 || last == null) return BigDecimal.ZERO;
        return last.subtract(first).multiply(HUNDRED).divide(first, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal extractBenchmarkReturn(ScenarioResponse scenario, String code) {
        return scenario.series().stream()
                .filter(s -> s.instrument().code().equals(code))
                .findFirst()
                .map(ScenarioSeries::nominalReturnPct)
                .orElse(BigDecimal.ZERO);
    }

    private InflationBeaterEntry toEntry(ScenarioSeries series, BigDecimal benchmarkReturn,
                                         Map<String, String> nameLookup) {
        BigDecimal nominal = series.nominalReturnPct();
        BigDecimal excess = nominal != null && benchmarkReturn != null
                ? nominal.subtract(benchmarkReturn)
                : null;
        boolean beats = excess != null && excess.signum() > 0;
        String key = series.instrument().type() + "|" + series.instrument().code();
        String name = nameLookup.getOrDefault(key, series.instrument().code());
        return new InflationBeaterEntry(
                series.instrument().type(),
                series.instrument().code(),
                name,
                nominal,
                excess,
                beats);
    }

    private record CuratedAsset(AnalyticsInstrumentType type, String code, String name) {}

    private record CachedResult(InflationBeaterResponse response, long expiresAt) {}
}
