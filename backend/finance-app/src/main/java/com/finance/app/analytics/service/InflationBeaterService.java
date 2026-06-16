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
import com.finance.market.macro.model.DepositMaturity;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import com.finance.shared.util.ReturnMath;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    // Excess decimals — matches ScenarioService RETURN_SCALE so excess and nominal share precision.
    private static final int EXCESS_SCALE = 4;

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

    // Categories the UI offers as beater benchmarks (mirrors the frontend's BENCHMARK_CATEGORIES). The
    // warm-up enumerates every indicator in these so all selectable benchmarks are cached, not a subset.
    private static final List<MacroCategory> BENCHMARK_CATEGORIES =
            List.of(MacroCategory.INFLATION, MacroCategory.RATES, MacroCategory.DEPOSIT);

    // Comparison currencies a benchmark can frame the universe in (TRY index + USD/EUR deposits/rates).
    private static final List<Currency> WARM_CURRENCIES = List.of(Currency.TRY, Currency.USD, Currency.EUR);

    private final ScenarioService scenarioService;
    private final UnifiedHistoryService historyService;
    private final MacroIndicatorQueryService macroQueryService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final BeaterCacheManager cacheManager;

    // The universe's per-asset return depends only on (period, comparison currency) — never on which
    // benchmark it is compared against — so the ~2700-asset universe is simulated ONCE per (period,
    // currency) and reused across every benchmark of that currency. Caching the LIGHTWEIGHT per-asset
    // summary (not the heavy daily series) keeps this to a few MB. 15-min TTL spans a warm run; the daily
    // warm clears it first so it always reflects the evening refresh. Holds 6 periods × 3 currencies = 18.
    private final Cache<String, List<UniverseReturn>> universeCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(64)
            .build();

    private final ObjectProvider<AssetNativeCurrencyResolver> nativeCurrencyResolver;

    // Cold-start guard. The scheduled warm-up waits for the market-data init, but a USER request isn't gated —
    // on a fresh empty DB compute() would 404 on the not-yet-persisted benchmark macro (findByCode throws) or
    // hammer external APIs against an empty DB. Injected optionally: absent (init disabled) ⇒ treated as ready.
    private final ObjectProvider<com.finance.app.config.MarketDataInitializer> marketDataInitializer;

    /**
     * Ranks the universe for the period against the benchmark in the benchmark-derived comparison currency
     * (no override). Convenience overload of {@link #rank(String, String, String)}.
     */
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
        if (!dataReady()) {
            // Market data is still cold-loading: return an empty ranking so the page degrades to "no data yet"
            // instead of a 404 / an external-API storm. The scheduled warm-up fills the cache once init finishes.
            return emptyRanking(period, code, override);
        }
        String key = cacheManager.buildKey(period, code, override);
        return cacheManager.getOrCompute(key, () -> compute(period, code, months, override));
    }

    /** True once the cold-start market-data init has finished (or when no initializer is wired). */
    private boolean dataReady() {
        com.finance.app.config.MarketDataInitializer initializer = marketDataInitializer.getIfAvailable();
        return initializer == null || initializer.completion().isDone();
    }

    /** Empty ranking for the window — used while market data is still loading (no benchmark/universe yet). */
    private InflationBeaterResponse emptyRanking(String period, String code, Currency override) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(PERIOD_MONTHS.getOrDefault(period, 12));
        Currency ccy = override != null ? override : Currency.TRY;
        return new InflationBeaterResponse(start, end, code, code, BigDecimal.ZERO, 0, 0, ccy, List.of());
    }

    /** Returns a cached ranking without computing; null on a cold cache (caller may {@link #warmAsync}). */
    public InflationBeaterResponse peekCache(String period, String benchmarkCode) {
        Integer months = PERIOD_MONTHS.get(period);
        if (months == null) return null;
        String code = resolveBenchmarkCode(benchmarkCode);
        return cacheManager.peek(cacheManager.buildKey(period, code, null));
    }

    /**
     * Fire-and-forget warm of the cache entry for the period/benchmark (TRY framing). No-op while market
     * data is still cold-loading or the period token is unknown; the cache manager dedupes concurrent warms.
     */
    public void warmAsync(String period, String benchmarkCode) {
        if (!dataReady()) return;
        Integer months = PERIOD_MONTHS.get(period);
        if (months == null) return;
        String code = resolveBenchmarkCode(benchmarkCode);
        String key = cacheManager.buildKey(period, code, null);
        cacheManager.warmAsync(key, period, code, () -> compute(period, code, months, null));
    }

    /**
     * Recomputes and replaces the cache entry for the period/benchmark (TRY framing), even if already
     * present. No-op for an unknown period token. Used by the scheduled refresh to keep rankings current.
     */
    public void refresh(String period, String benchmarkCode) {
        Integer months = PERIOD_MONTHS.get(period);
        if (months == null) return;
        String code = resolveBenchmarkCode(benchmarkCode);
        String key = cacheManager.buildKey(period, code, null);
        cacheManager.refresh(key, period, code, () -> compute(period, code, months, null));
    }

    /** Drops all cached rankings (and the per-(period,currency) universe sims), forcing a fresh recompute. */
    public void clearCache() {
        cacheManager.clear();
        universeCache.invalidateAll();
    }

    /**
     * Every macro indicator the UI offers as a beater benchmark — all INFLATION, RATES and DEPOSIT series,
     * enumerated from the catalog (mirrors the frontend's BENCHMARK_CATEGORIES filter). Drives the warm-up so
     * every selectable benchmark is cached and a newly published series warms automatically, rather than a
     * hard-coded config subset. A category that fails to enumerate is logged and skipped, not fatal.
     */
    public List<String> warmableBenchmarkCodes() {
        List<String> codes = new ArrayList<>();
        for (MacroCategory category : BENCHMARK_CATEGORIES) {
            try {
                for (MacroIndicator m : macroQueryService.listByCategory(category)) {
                    if (m.getCode() != null) codes.add(m.getCode());
                }
            } catch (RuntimeException e) {
                log.warn("Failed to enumerate benchmark category {}: {}", category, e.getMessage());
            }
        }
        return codes;
    }

    /** Every period token the ranking supports (1M..5Y); the warm-up caches all of them, not a config subset. */
    public Set<String> warmablePeriods() {
        return PERIOD_MONTHS.keySet();
    }

    /**
     * Canonicalises the requested benchmark to its raw EVDS code. The API speaks EVDS-free public slugs
     * ({@code cpiindex}, {@code deposittry1m}), but the cache, the scheduled warm-up and the universe all key on
     * the raw EVDS code ({@code TP.GENENDEKS.T1}). Without this translation a slug request 404s in
     * {@link #compute} (findByCode only knows EVDS codes) and misses the warm cache the scheduler filled under
     * the EVDS key. {@link MacroIndicatorQueryService#findByPublicId} accepts either form, so this is idempotent
     * for the default/warm-up EVDS path. On a cold DB (no indicators yet) it falls back to the raw request, which
     * the {@code dataReady()} gate then degrades to an empty ranking instead of an error.
     */
    private String resolveBenchmarkCode(String benchmarkCode) {
        String requested = (benchmarkCode != null && !benchmarkCode.isBlank()) ? benchmarkCode : DEFAULT_BENCHMARK;
        try {
            MacroIndicator indicator = macroQueryService.findByPublicId(requested);
            String canonical = indicator == null ? null : indicator.getCode();
            return (canonical != null && !canonical.isBlank()) ? canonical : requested;
        } catch (RuntimeException e) {
            return requested;
        }
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
        // Window ends TODAY, not the benchmark's last published EVDS observation. Deposit interest accrues
        // daily (ScenarioService.simulateRate carries the last rate to endDate) and prices keep moving, so
        // anchoring to a lagging monthly/weekly print silently dropped the most recent days of real return.
        // A missing end-of-window benchmark print is harmless: computeIndexGrowthPct and the rate scenario
        // both use the latest value at or before endDate (carry-forward).
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);

        MacroIndicator benchmark = macroQueryService.findByCode(code);
        boolean isIndex = benchmark.getUnit() == MacroUnit.INDEX
                || benchmark.getUnit() == MacroUnit.NUMBER;

        // An INDEX/NUMBER benchmark's return is the raw TRY-basis index growth (computeIndexGrowthPct, no FX
        // conversion), so the universe must be simulated in TRY too — otherwise a USD/EUR-framed nominal is
        // compared against a TRY-basis CPI (~40%) and nearly the whole universe is wrongly flagged as not
        // beating inflation. A non-TRY override is only coherent for rate-backed benchmarks, whose return is
        // drawn from the same FX-framed scenario; force TRY for index benchmarks regardless of the override.
        Currency comparisonCurrency = isIndex
                ? Currency.TRY
                : (override != null ? override : resolveComparisonCurrency(benchmark, code));

        List<CuratedAsset> universe = buildUniverse();
        List<UniverseReturn> universeReturns =
                universeReturnsCached(period, startDate, endDate, comparisonCurrency, universe);
        log.info("Computing beaters period={} benchmark={} currency={} universeSize={}",
                period, code, comparisonCurrency, universe.size());

        BigDecimal benchmarkReturn = isIndex
                ? computeIndexGrowthPct(code, startDate, endDate)
                : benchmarkReturn(code, benchmark, universeReturns, startDate, endDate, comparisonCurrency);

        Map<String, String> nameLookup = nameMapFor(universe);
        List<InflationBeaterEntry> entries = universeReturns.stream()
                .filter(u -> isIndex || !u.code().equals(code))
                .filter(u -> u.nominalReturnPct() != null)
                .filter(u -> !u.partial())
                .map(u -> toEntry(u, benchmarkReturn, nameLookup))
                .sorted(Comparator.comparing(InflationBeaterEntry::excessReturnPct,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int beating = (int) entries.stream().filter(InflationBeaterEntry::beatsBenchmark).count();
        log.info("Beaters ranked period={} benchmark={} currency={} return={} total={} beating={}",
                period, code, comparisonCurrency, benchmarkReturn, entries.size(), beating);
        // Expose the EVDS-free public slug (not the raw EVDS code) so the frontend can match the benchmark
        // against its slug-keyed catalog — mirrors the API's slugs-only contract everywhere else.
        return new InflationBeaterResponse(
                startDate, endDate, benchmark.getSlug(), benchmark.getLabel(),
                benchmarkReturn, beating, entries.size(), comparisonCurrency, entries);
    }

    /**
     * Universe per-asset returns for (period, currency), cached so every benchmark of that currency reuses
     * one simulation. The heavy {@link ScenarioResponse} (full daily series) is projected to a lightweight
     * summary and discarded; only the per-asset return + partial flag are kept.
     */
    private List<UniverseReturn> universeReturnsCached(String period, LocalDate startDate, LocalDate endDate,
                                                       Currency currency, List<CuratedAsset> universe) {
        return universeCache.get(period + "|" + currency.name(),
                k -> simulateUniverse(startDate, endDate, currency, universe));
    }

    private List<UniverseReturn> simulateUniverse(LocalDate startDate, LocalDate endDate,
                                                  Currency currency, List<CuratedAsset> universe) {
        List<AnalyticsInstrument> instruments = new ArrayList<>(universe.size());
        for (CuratedAsset c : universe) {
            instruments.add(new AnalyticsInstrument(c.type, c.code));
        }
        ScenarioResponse scenario = scenarioService.simulate(
                new ScenarioRequest(NOTIONAL_AMOUNT, startDate, endDate, instruments, currency));
        List<UniverseReturn> out = new ArrayList<>(scenario.series().size());
        for (ScenarioSeries s : scenario.series()) {
            out.add(new UniverseReturn(s.instrument().type(), s.instrument().code(),
                    s.nominalReturnPct(), s.partial()));
        }
        return out;
    }

    /**
     * Return of a rate-backed benchmark: a deposit benchmark is itself ranked in the universe, so its return
     * is read straight from the cached universe; a RATES benchmark (not in the universe) is simulated alone.
     */
    private BigDecimal benchmarkReturn(String code, MacroIndicator benchmark, List<UniverseReturn> universeReturns,
                                       LocalDate startDate, LocalDate endDate, Currency currency) {
        for (UniverseReturn u : universeReturns) {
            if (u.code().equals(code) && u.nominalReturnPct() != null) {
                return u.nominalReturnPct();
            }
        }
        AnalyticsInstrumentType type = CATEGORY_TO_TYPE.getOrDefault(
                benchmark.getCategory(), AnalyticsInstrumentType.MACRO);
        ScenarioResponse one = scenarioService.simulate(new ScenarioRequest(
                NOTIONAL_AMOUNT, startDate, endDate, List.of(new AnalyticsInstrument(type, code)), currency));
        return extractBenchmarkReturn(one, code);
    }

    /**
     * Pre-simulates the universe for every (period × TRY/USD/EUR) so the scheduled warm-up's per-benchmark
     * rankings all read from the cache. Iterates period-outer/currency-inner so the three currency framings
     * of each period reuse that period's freshly-fetched raw history (which is currency-independent).
     */
    public void warmUniverse() {
        if (!dataReady()) return;
        List<CuratedAsset> universe = buildUniverse();
        LocalDate end = LocalDate.now();
        for (var e : PERIOD_MONTHS.entrySet()) {
            LocalDate start = end.minusMonths(e.getValue());
            for (Currency ccy : WARM_CURRENCIES) {
                try {
                    universeReturnsCached(e.getKey(), start, end, ccy, universe);
                } catch (RuntimeException ex) {
                    log.warn("Universe warm failed period={} currency={}: {}", e.getKey(), ccy, ex.getMessage());
                }
            }
        }
    }

    /**
     * Frames the comparison in the benchmark's own currency, e.g. a USD/EUR deposit benchmark is
     * compared in USD/EUR. Falls back to TRY when no native-currency resolver is wired or none resolves.
     */
    private Currency resolveComparisonCurrency(MacroIndicator benchmark, String code) {
        AssetNativeCurrencyResolver resolver = nativeCurrencyResolver.getIfAvailable();
        if (resolver == null) {
            return Currency.TRY;
        }
        MarketType marketType = CATEGORY_TO_MARKET_TYPE.getOrDefault(
                benchmark.getCategory(), MarketType.MACRO_RATE);
        Currency resolved = resolver.resolveNativeCurrency(marketType, code);
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
        universe.addAll(depositUniverse());
        return universe;
    }

    /**
     * Deposits enumerated from the macro catalog (every published TRY/USD/EUR tenor), not a hard-coded list —
     * mirroring how stocks/crypto/forex/commodity are enumerated, so newly added deposit tenors rank
     * automatically. The synthetic {@link DepositMaturity#TOTAL} bucket is excluded: it is the weighted
     * average across the other tenors, so ranking it beside them would double-count an aggregate that is not
     * itself a holdable product.
     */
    private List<CuratedAsset> depositUniverse() {
        List<CuratedAsset> deposits = new ArrayList<>();
        try {
            for (MacroIndicator d : macroQueryService.listByCategory(MacroCategory.DEPOSIT)) {
                if (d.getMaturity() == DepositMaturity.TOTAL) continue;
                deposits.add(new CuratedAsset(AnalyticsInstrumentType.DEPOSIT, d.getCode(), depositName(d)));
            }
        } catch (Exception e) {
            log.warn("Failed to enumerate deposit universe: {}", e.getMessage());
        }
        return deposits;
    }

    /** Fallback display name "{CURRENCY} {tenor}" (e.g. "TRY 3M"); the frontend localizes via the label key. */
    private static String depositName(MacroIndicator d) {
        String currency = d.getCurrency() != null ? d.getCurrency() + " " : "";
        String tenor = d.getMaturity() != null ? d.getMaturity().tenorLabel() : d.getCode();
        return currency + tenor;
    }

    private Map<String, String> nameMapFor(List<CuratedAsset> universe) {
        Map<String, String> out = new LinkedHashMap<>();
        for (CuratedAsset c : universe) {
            out.put(c.type + "|" + c.code, c.name);
        }
        return out;
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

    private InflationBeaterEntry toEntry(UniverseReturn u, BigDecimal benchmarkReturn,
                                         Map<String, String> nameLookup) {
        // Geometric (Fisher) excess: how much the asset REALLY beat the benchmark by in purchasing-power
        // terms — ((1+nominal)/(1+benchmark)-1) — NOT the naive arithmetic nominal-benchmark, which
        // overstates outperformance ~5x at Turkish inflation levels. The sign is identical, so the "beats"
        // verdict and the excess-DESC ranking are unchanged; only the reported magnitude is corrected.
        BigDecimal excess = ReturnMath.realExcessPct(u.nominalReturnPct(), benchmarkReturn, EXCESS_SCALE);
        boolean beats = excess != null && excess.signum() > 0;
        String key = u.type() + "|" + u.code();
        String name = nameLookup.getOrDefault(key, u.code());
        return new InflationBeaterEntry(u.type(), u.code(), name, u.nominalReturnPct(), excess, beats);
    }

    /** Curated universe member to rank. */
    private record CuratedAsset(AnalyticsInstrumentType type, String code, String name) {}

    /** Lightweight projection of a simulated universe asset — its nominal return and completeness. */
    private record UniverseReturn(AnalyticsInstrumentType type, String code,
                                  BigDecimal nominalReturnPct, boolean partial) {}
}
