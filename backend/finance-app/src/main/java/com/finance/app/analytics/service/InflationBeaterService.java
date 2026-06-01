package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.response.InflationBeaterEntry;
import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.dto.response.ScenarioSeries;
import com.finance.common.exception.BadRequestException;
import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.AssetNativeCurrencyResolver;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks a curated universe of instruments (tracked stocks/crypto/forex/funds/commodities plus standard
 * deposits) by their notional return over a period against a benchmark macro indicator, flagging which
 * "beat" it. Each ranking is computed via {@link ScenarioService} in a single comparison currency and
 * cached (see {@link BeaterCacheManager}); results power the inflation-beater view and overview widget.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class InflationBeaterService {

    // TÜFE (consumer-price index) is the canonical inflation benchmark. The TCMB EVDS
    // code naming is misleading: TP.GENENDEKS.T1 is TÜFE and TP.TUFE1YI.T1 is Yİ-ÜFE
    // (verified against TÜİK published values).
    private static final String DEFAULT_BENCHMARK = "TP.GENENDEKS.T1";
    private static final BigDecimal NOTIONAL_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    // Codes and labels follow macro.yaml: MT02=3M (M3), MT03=6M (M6), MT04=1Y (M12). MT12 does not
    // exist in the config and must not be referenced.
    private static final List<CuratedAsset> CURATED_DEPOSITS = List.of(
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.TRYTAS.MT02", "TRY 3M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.TRYTAS.MT03", "TRY 6M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.TRYTAS.MT04", "TRY 1Y Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.USDTAS.MT02", "USD 3M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.USDTAS.MT03", "USD 6M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.USDTAS.MT04", "USD 1Y Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.EURTAS.MT02", "EUR 3M Mevduat"),
            new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, "TP.EURTAS.MT03", "EUR 6M Mevduat")
    );

    private static final Map<TrackedAssetType, AnalyticsInstrumentType> TYPE_MAP = Map.of(
            TrackedAssetType.STOCK,     AnalyticsInstrumentType.SPOT,
            TrackedAssetType.CRYPTO,    AnalyticsInstrumentType.CRYPTO,
            TrackedAssetType.FOREX,     AnalyticsInstrumentType.FOREX,
            TrackedAssetType.FUND,      AnalyticsInstrumentType.FUND,
            TrackedAssetType.COMMODITY, AnalyticsInstrumentType.COMMODITY
    );

    private static final Map<String, Integer> PERIOD_MONTHS = Map.of(
            "1M", 1, "3M", 3, "6M", 6, "1Y", 12, "3Y", 36, "5Y", 60
    );

    private static final Map<MacroCategory, AnalyticsInstrumentType> CATEGORY_TO_TYPE = Map.of(
            MacroCategory.DEPOSIT, AnalyticsInstrumentType.DEPOSIT,
            MacroCategory.RATES, AnalyticsInstrumentType.MACRO,
            MacroCategory.INFLATION, AnalyticsInstrumentType.MACRO
    );

    private static final Map<MacroCategory, MarketType> CATEGORY_TO_MARKET_TYPE = Map.of(
            MacroCategory.DEPOSIT, MarketType.MACRO_DEPOSIT,
            MacroCategory.RATES, MarketType.MACRO_RATE,
            MacroCategory.INFLATION, MarketType.MACRO_INFLATION
    );

    private final ScenarioService scenarioService;
    private final UnifiedHistoryService historyService;
    private final MacroIndicatorQueryService macroQueryService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final BeaterCacheManager cacheManager;

    @Autowired(required = false)
    private AssetNativeCurrencyResolver nativeCurrencyResolver;

    public InflationBeaterResponse rank(String period, String benchmarkCode) {
        return rank(period, benchmarkCode, null);
    }

    /**
     * Cache-first ranking for the period (1M..5Y); {@code targetCurrencyOverride} forces the comparison
     * currency, otherwise it is derived from the benchmark.
     *
     * @throws BadRequestException if the period token is unknown
     */
    public InflationBeaterResponse rank(String period, String benchmarkCode, String targetCurrencyOverride) {
        Integer months = PERIOD_MONTHS.get(period);
        if (months == null) {
            throw new BadRequestException("error.analytics.unknownPeriod", period);
        }
        String code = resolveBenchmarkCode(benchmarkCode);
        Currency override = parseCurrencyOverride(targetCurrencyOverride);
        String key = cacheManager.buildKey(period, code, override);
        return cacheManager.getOrCompute(key, () -> compute(period, code, months, override));
    }

    /** Returns a cached ranking without computing; null on a cold cache (caller may {@link #warmAsync}). */
    public InflationBeaterResponse peekCache(String period, String benchmarkCode) {
        Integer months = PERIOD_MONTHS.get(period);
        if (months == null) return null;
        String code = resolveBenchmarkCode(benchmarkCode);
        return cacheManager.peek(cacheManager.buildKey(period, code, null));
    }

    public void warmAsync(String period, String benchmarkCode) {
        Integer months = PERIOD_MONTHS.get(period);
        if (months == null) return;
        String code = resolveBenchmarkCode(benchmarkCode);
        String key = cacheManager.buildKey(period, code, null);
        cacheManager.warmAsync(key, period, code, () -> compute(period, code, months, null));
    }

    public void refresh(String period, String benchmarkCode) {
        Integer months = PERIOD_MONTHS.get(period);
        if (months == null) return;
        String code = resolveBenchmarkCode(benchmarkCode);
        String key = cacheManager.buildKey(period, code, null);
        cacheManager.refresh(key, period, code, () -> compute(period, code, months, null));
    }

    public void clearCache() {
        cacheManager.clear();
    }

    private static String resolveBenchmarkCode(String benchmarkCode) {
        return (benchmarkCode != null && !benchmarkCode.isBlank()) ? benchmarkCode : DEFAULT_BENCHMARK;
    }

    private static Currency parseCurrencyOverride(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Currency.fromCode(raw);
    }

    /**
     * Builds the full ranking: simulates the universe (and the benchmark itself when it is rate-backed)
     * in the comparison currency, derives the benchmark return (index growth for index-unit benchmarks),
     * then sorts entries by excess return descending. Partial/incomplete series are dropped.
     */
    private InflationBeaterResponse compute(String period, String code, int months, Currency override) {
        LocalDate endDate = resolveStableEndDate(code);
        LocalDate startDate = endDate.minusMonths(months);

        MacroIndicator benchmark = macroQueryService.findByCode(code);
        boolean isIndex = benchmark.getUnit() == MacroUnit.INDEX
                || benchmark.getUnit() == MacroUnit.NUMBER;

        Currency comparisonCurrency = override != null
                ? override
                : resolveComparisonCurrency(benchmark, code);

        List<CuratedAsset> universe = buildUniverse();
        log.info("Computing beaters period={} benchmark={} currency={} universeSize={}",
                period, code, comparisonCurrency, universe.size());

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
                new ScenarioRequest(NOTIONAL_AMOUNT, startDate, endDate, instruments, comparisonCurrency));

        BigDecimal benchmarkReturn = isIndex
                ? computeIndexGrowthPct(code, startDate, endDate)
                : extractBenchmarkReturn(scenario, code);

        Map<String, String> nameLookup = nameMapFor(universe);
        List<InflationBeaterEntry> entries = scenario.series().stream()
                .filter(s -> !s.instrument().code().equals(code) || isIndex)
                .filter(s -> s.nominalReturnPct() != null)
                .filter(s -> !s.partial())
                .map(s -> toEntry(s, benchmarkReturn, nameLookup))
                .sorted(Comparator.comparing(InflationBeaterEntry::excessReturnPct,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int beating = (int) entries.stream().filter(InflationBeaterEntry::beatsBenchmark).count();
        log.info("Beaters ranked period={} benchmark={} currency={} return={} total={} beating={}",
                period, code, comparisonCurrency, benchmarkReturn, entries.size(), beating);
        return new InflationBeaterResponse(
                startDate, endDate, code, benchmark.getLabel(),
                benchmarkReturn, beating, entries.size(), comparisonCurrency, entries);
    }

    /**
     * Frames the comparison in the benchmark's own currency, e.g. a USD/EUR deposit benchmark is
     * compared in USD/EUR. Falls back to TRY when no native-currency resolver is wired or none resolves.
     */
    private Currency resolveComparisonCurrency(MacroIndicator benchmark, String code) {
        if (nativeCurrencyResolver == null) {
            return Currency.TRY;
        }
        MarketType marketType = CATEGORY_TO_MARKET_TYPE.getOrDefault(
                benchmark.getCategory(), MarketType.MACRO_RATE);
        Currency resolved = nativeCurrencyResolver.resolveNativeCurrency(marketType, code);
        return resolved != null ? resolved : Currency.TRY;
    }

    private List<CuratedAsset> buildUniverse() {
        List<CuratedAsset> universe = new ArrayList<>();
        for (var entry : TYPE_MAP.entrySet()) {
            TrackedAssetType trackedType = entry.getKey();
            AnalyticsInstrumentType analyticsType = entry.getValue();
            try {
                List<String> codes = trackedAssetQueryService.getEnabledCodes(trackedType);
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

    /**
     * Anchors the window end to the benchmark's latest actual observation (not today), so a lagging
     * monthly indicator doesn't yield a window with no benchmark data point near the end.
     */
    private LocalDate resolveStableEndDate(String code) {
        LocalDate today = LocalDate.now();
        List<HistoryPoint> recent = historyService.getMacroSeries(code,
                today.minusMonths(3), today);
        LocalDate latest = null;
        for (HistoryPoint p : recent) {
            if (p == null || p.date() == null || p.value() == null) continue;
            if (latest == null || p.date().isAfter(latest)) latest = p.date();
        }
        return latest != null ? latest : today;
    }

    /**
     * Percentage growth of an index-unit benchmark (e.g. CPI) between the observations closest to the
     * window endpoints; used instead of a compounding simulation for index benchmarks.
     */
    private BigDecimal computeIndexGrowthPct(String code, LocalDate start, LocalDate end) {
        List<HistoryPoint> points = historyService.getMacroSeries(code,
                start.minusMonths(2), end.plusMonths(1));
        if (points.size() < 2) return BigDecimal.ZERO;
        HistoryPoint baseline = null;
        HistoryPoint latest = null;
        for (HistoryPoint p : points) {
            if (p.date() == null || p.value() == null) continue;
            if (!p.date().isAfter(end) && (latest == null || p.date().isAfter(latest.date()))) {
                latest = p;
            }
            if (!p.date().isAfter(start) && (baseline == null || p.date().isAfter(baseline.date()))) {
                baseline = p;
            }
        }
        if (baseline == null) {
            for (HistoryPoint p : points) {
                if (p.date() == null || p.value() == null) continue;
                if (!p.date().isBefore(start) && (baseline == null || p.date().isBefore(baseline.date()))) {
                    baseline = p;
                }
            }
        }
        if (baseline == null || latest == null) return BigDecimal.ZERO;
        BigDecimal first = baseline.value();
        BigDecimal last = latest.value();
        if (first.signum() <= 0) return BigDecimal.ZERO;
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
}
