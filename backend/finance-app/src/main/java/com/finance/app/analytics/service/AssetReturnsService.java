package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.ReturnPeriod;
import com.finance.app.analytics.dto.RiskLevel;
import com.finance.app.analytics.dto.response.AssetReturnRow;
import com.finance.app.analytics.dto.response.AssetReturnsResponse;
import com.finance.app.analytics.dto.response.PeriodReturn;
import com.finance.common.model.Currency;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.core.service.TrackedAssetQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Ranks every tracked SPOT asset (stocks, crypto, forex, funds, commodities) by its REALIZED return over
 * each window (1W..5Y); bonds and VIOP are excluded (short-dated, valued differently). For each asset one TRY
 * price series is fetched once over the longest window via {@link UnifiedHistoryService} — which already
 * converts crypto USD→TRY per date with nearest-rate fallback — and every window's return %, per-unit change
 * and annualized volatility are sliced from that series.
 *
 * <p>The same series is also expressed in USD and EUR (each point converted at its own date's FX via
 * {@link CurrencyConverter}, the same per-date FX as Compare) so each currency carries its own figures and is
 * its own ranking on the frontend — a foreign-currency return reflects the FX move, not just the lira's. TRY
 * drives a window's inclusion; the FX legs are best-effort and null when FX history doesn't cover the window.
 * The whole multi-currency dataset is cached in-app (see {@link AssetReturnsCacheManager}) and refreshed daily
 * after the evening market-data refresh. On a cold start (market-data init not finished) it returns an empty
 * dataset so the page degrades to "preparing" instead of querying an empty DB.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class AssetReturnsService {

    private static final Map<TrackedAssetType, AnalyticsInstrumentType> TYPE_MAP = Map.of(
            TrackedAssetType.STOCK,     AnalyticsInstrumentType.SPOT,
            TrackedAssetType.CRYPTO,    AnalyticsInstrumentType.CRYPTO,
            TrackedAssetType.FOREX,     AnalyticsInstrumentType.FOREX,
            TrackedAssetType.FUND,      AnalyticsInstrumentType.FUND,
            TrackedAssetType.COMMODITY, AnalyticsInstrumentType.COMMODITY
    );

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    // A long position can lose at most its whole value, so returns are floored at −100% — and a still-priced
    // asset never truly reaches it. A −99.99x% loss that 2-dp rounding pushes to exactly −100% is pinned just
    // under, so the UI never prints a phantom "−100%" total wipeout.
    private static final BigDecimal MIN_RETURN_PCT = new BigDecimal("-100");
    private static final BigDecimal NEAR_TOTAL_LOSS_PCT = new BigDecimal("-99.99");
    // 6 dp so sub-cent unit prices (small "serbest fon" units, minor currencies like KRW) don't round to
    // 0 in the response; the frontend trims trailing precision per magnitude for display.
    private static final int PRICE_SCALE = 6;
    private static final int PCT_SCALE = 2;
    private static final int TRADING_DAYS_PER_YEAR = 252;
    private static final int MIN_POINTS_FOR_VOLATILITY = 4;
    // ±30-day tolerance for "does the asset's history cover this window" — mirrors the beater. A window whose
    // start the data doesn't reach within this many days is dropped, not ranked on a shorter span.
    private static final int PARTIAL_TOLERANCE_DAYS = 30;

    // Annualized-volatility risk bands (percent). Tunable: Turkish spot assets are volatile, so the bands sit
    // higher than a developed-market default — below 25% low, 25–55% medium, above 55% high.
    private static final BigDecimal RISK_LOW_MAX = new BigDecimal("25");
    private static final BigDecimal RISK_MEDIUM_MAX = new BigDecimal("55");

    // The non-TRY currencies the dataset is ALSO expressed in (same math, FX-converted series) — each its own
    // ranking on the frontend. TRY stays the inclusion driver; these degrade to null when FX is missing.
    private static final List<Currency> FX_CURRENCIES = List.of(Currency.USD, Currency.EUR);

    private final UnifiedHistoryService historyService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final AssetReturnsCacheManager cacheManager;
    private final CurrencyConverter currencyConverter;

    // Cold-start guard — same rationale as the beater: a USER request on a still-empty DB would yield only
    // empties / churn, so gate it; the page shows "preparing" until the scheduled warm-up fills the cache.
    @Autowired(required = false)
    private com.finance.app.config.MarketDataInitializer marketDataInitializer;

    /** Cache-first dataset; an empty asset list signals the data isn't ready yet (cold start). */
    public AssetReturnsResponse getReturns() {
        if (!dataReady()) {
            return new AssetReturnsResponse(LocalDate.now(), List.of());
        }
        return cacheManager.getOrCompute(this::compute);
    }

    /**
     * Cached dataset WITHOUT computing: returns null on a cold cache (or before market data is ready) so a
     * caller never blocks the request thread on the multi-asset 5Y compute. The overview widget uses this and
     * triggers {@link #warmAsync()} on a miss, mirroring how the beater widget peeks then warms.
     */
    public AssetReturnsResponse peekReturns() {
        if (!dataReady()) {
            return null;
        }
        return cacheManager.peek();
    }

    /** Recomputes and caches the dataset (startup/daily warm-up); no-op while market data is cold-loading. */
    public void warmCache() {
        if (!dataReady()) {
            return;
        }
        cacheManager.refresh(this::compute);
    }

    /** Off-request-thread warm-up for a cold cache hit by a widget peek; no-op while market data is cold. */
    @org.springframework.scheduling.annotation.Async
    public void warmAsync() {
        warmCache();
    }

    public void clearCache() {
        cacheManager.clear();
    }

    /** True once the cold-start market-data init has finished (or when no initializer is wired). */
    private boolean dataReady() {
        return marketDataInitializer == null || marketDataInitializer.completion().isDone();
    }

    private AssetReturnsResponse compute() {
        LocalDate today = LocalDate.now();
        // One fetch per asset over the longest window (plus a month of lead-in so a baseline point exists at
        // or before the 5Y start); every shorter window is sliced from the same series.
        LocalDate fetchFrom = ReturnPeriod.FIVE_YEARS.startFrom(today).minusMonths(1);

        List<AssetReturnRow> rows = new ArrayList<>();
        for (CuratedAsset asset : buildUniverse()) {
            List<HistoryPoint> series = safeSeries(asset, fetchFrom, today);
            if (series.size() < 2) {
                continue;
            }
            // Express the TRY series in each FX currency ONCE (each point converted at its own date), reused
            // across every window — so a USD/EUR figure is the same calc as TRY, just on the converted series.
            Map<Currency, List<HistoryPoint>> fxSeries = new EnumMap<>(Currency.class);
            for (Currency ccy : FX_CURRENCIES) {
                fxSeries.put(ccy, convertSeriesTo(series, ccy));
            }
            Map<String, PeriodReturn> periods = new LinkedHashMap<>();
            for (ReturnPeriod period : ReturnPeriod.values()) {
                PeriodReturn pr = computePeriod(series, fxSeries, period, today);
                if (pr != null) {
                    periods.put(period.token(), pr);
                }
            }
            if (!periods.isEmpty()) {
                rows.add(new AssetReturnRow(asset.type(), asset.code(), asset.name(), periods));
            }
        }
        log.info("Asset returns computed assets={} asOf={}", rows.size(), today);
        return new AssetReturnsResponse(today, rows);
    }

    private List<HistoryPoint> safeSeries(CuratedAsset asset, LocalDate from, LocalDate to) {
        try {
            return historyService.getSeries(new AnalyticsInstrument(asset.type(), asset.code()), from, to);
        } catch (RuntimeException e) {
            log.warn("Asset returns series fetch failed type={} code={}: {}",
                    asset.type(), asset.code(), e.getMessage());
            return List.of();
        }
    }

    /**
     * One window's return across currencies: TRY (top-level) plus each FX currency, from the pre-converted
     * series. TRY drives inclusion — null here (no usable pair, or the history doesn't reach the window start
     * within the ±30-day tolerance) omits the period from the row, mirroring how the beater drops a span it
     * can't cover. The FX legs are best-effort: null when their converted series can't form the pair.
     */
    private PeriodReturn computePeriod(List<HistoryPoint> trySeries,
                                       Map<Currency, List<HistoryPoint>> fxSeries,
                                       ReturnPeriod period, LocalDate end) {
        Figures tryFigures = figuresFor(trySeries, period, end);
        if (tryFigures == null) {
            return null;
        }
        return new PeriodReturn(
                tryFigures.returnPct(), tryFigures.returnValue(), tryFigures.priceThen(),
                tryFigures.priceNow(), tryFigures.volatility(), tryFigures.riskLevel(),
                currencyFigures(fxSeries.get(Currency.USD), period, end),
                currencyFigures(fxSeries.get(Currency.EUR), period, end));
    }

    /** {@link PeriodReturn.CurrencyFigures} for one FX-converted series, or null when it can't form the pair. */
    private static PeriodReturn.CurrencyFigures currencyFigures(List<HistoryPoint> series,
                                                                ReturnPeriod period, LocalDate end) {
        Figures f = figuresFor(series, period, end);
        if (f == null) {
            return null;
        }
        return new PeriodReturn.CurrencyFigures(
                f.returnPct(), f.returnValue(), f.priceThen(), f.priceNow(), f.volatility(), f.riskLevel());
    }

    /**
     * Expresses a TRY price series in {@code to}, each point converted at its OWN date's FX (nearest rate
     * on/before, via {@link CurrencyConverter}); dates with no rate are dropped. Returns an empty list — so
     * that currency's figures come back null and the row degrades to "no FX for this window" — when conversion
     * isn't possible, rather than failing the whole dataset.
     */
    private List<HistoryPoint> convertSeriesTo(List<HistoryPoint> trySeries, Currency to) {
        Map<LocalDate, BigDecimal> input = new LinkedHashMap<>();
        for (HistoryPoint p : trySeries) {
            if (p.date() != null && p.value() != null) {
                input.put(p.date(), p.value());
            }
        }
        SortedMap<LocalDate, BigDecimal> converted;
        try {
            converted = currencyConverter.convertSeries(input, Currency.TRY, to);
        } catch (RuntimeException e) {
            log.warn("FX series conversion to {} failed: {}", to, e.getMessage());
            return List.of();
        }
        if (converted == null || converted.isEmpty()) {
            return List.of();
        }
        List<HistoryPoint> out = new ArrayList<>(converted.size());
        converted.forEach((date, value) -> out.add(new HistoryPoint(date, value)));
        return out;
    }

    /**
     * Realized-return figures for one window from a single series: {@code now} = the latest price; {@code then}
     * = the baseline near the window start. Null — so the window is omitted — when there's no usable price pair
     * or the series doesn't reach the window start within the ±30-day tolerance. Currency-agnostic: the same
     * math runs over the TRY series and over each FX-converted series.
     */
    private static Figures figuresFor(List<HistoryPoint> series, ReturnPeriod period, LocalDate notAfter) {
        if (series == null || series.size() < 2) {
            return null;
        }
        // Anchor the window to the asset's LAST available data point, not the calendar 'notAfter' (today). Fund
        // and market NAVs publish T+1 and not on weekends/holidays, so the latest point is usually a few days
        // old; official (TEFAS) returns measure the trailing window from the last PRICED day, so anchoring to
        // 'today' instead shifts the baseline by the staleness gap and drifts the return off the official figure.
        // This is the same "use the last available reading" rule already applied to FX rates and the end price.
        HistoryPoint now = latestAtOrBefore(series, notAfter);
        if (now == null) {
            return null;
        }
        LocalDate anchor = now.date();
        LocalDate start = period.startFrom(anchor);
        HistoryPoint then = baselineNear(series, start);
        if (then == null) {
            return null;
        }
        BigDecimal priceNow = now.value();
        BigDecimal priceThen = then.value();
        if (priceNow == null || priceThen == null || priceThen.signum() <= 0) {
            return null;
        }
        BigDecimal returnPct = priceNow.subtract(priceThen)
                .multiply(HUNDRED)
                .divide(priceThen, PCT_SCALE, RoundingMode.HALF_UP);
        // Never let a still-priced asset read as a full −100% wipeout; that only happens when rounding nudges a
        // −99.99x% loss to exactly −100% (e.g. a fund that lost almost all its value).
        if (returnPct.compareTo(MIN_RETURN_PCT) <= 0) {
            returnPct = NEAR_TOTAL_LOSS_PCT;
        }
        BigDecimal returnValue = priceNow.subtract(priceThen).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal volatility = annualizedVolatilityPct(series, start, anchor);
        return new Figures(
                returnPct, returnValue,
                priceThen.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                priceNow.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                volatility, riskLevel(volatility));
    }

    /** Currency-agnostic window figures: the shared output of {@link #figuresFor} before currency labeling. */
    private record Figures(BigDecimal returnPct, BigDecimal returnValue, BigDecimal priceThen,
                           BigDecimal priceNow, BigDecimal volatility, RiskLevel riskLevel) {}

    /** Latest point on or before {@code date} (the series is date-ascending). */
    private static HistoryPoint latestAtOrBefore(List<HistoryPoint> series, LocalDate date) {
        HistoryPoint found = null;
        for (HistoryPoint p : series) {
            if (p.date() == null || p.value() == null) {
                continue;
            }
            if (p.date().isAfter(date)) {
                break;
            }
            found = p;
        }
        return found;
    }

    /**
     * Baseline for a window start: the latest point on/before it (carry-forward — valid however old it is).
     * When the asset is younger than the window (no point on/before the start), the earliest point is accepted
     * only if it lands within {@link #PARTIAL_TOLERANCE_DAYS} of the start; otherwise the asset doesn't really
     * cover this window, so null is returned and the period is dropped (the ±tolerance the beater uses).
     */
    private static HistoryPoint baselineNear(List<HistoryPoint> series, LocalDate start) {
        HistoryPoint atOrBefore = latestAtOrBefore(series, start);
        if (atOrBefore != null) {
            return atOrBefore;
        }
        LocalDate cutoff = start.plusDays(PARTIAL_TOLERANCE_DAYS);
        for (HistoryPoint p : series) {
            if (p.date() == null || p.value() == null || p.date().isBefore(start)) {
                continue;
            }
            return p.date().isAfter(cutoff) ? null : p;
        }
        return null;
    }

    /**
     * Annualized volatility (%) over the window: the sample standard deviation of daily log returns × √252 ×
     * 100. Null when fewer than {@link #MIN_POINTS_FOR_VOLATILITY} usable points fall inside the window.
     */
    private static BigDecimal annualizedVolatilityPct(List<HistoryPoint> series, LocalDate start, LocalDate end) {
        List<Double> prices = new ArrayList<>();
        for (HistoryPoint p : series) {
            if (p.date() == null || p.value() == null) {
                continue;
            }
            if (p.date().isBefore(start) || p.date().isAfter(end) || p.value().signum() <= 0) {
                continue;
            }
            prices.add(p.value().doubleValue());
        }
        if (prices.size() < MIN_POINTS_FOR_VOLATILITY) {
            return null;
        }
        List<Double> logReturns = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            logReturns.add(Math.log(prices.get(i) / prices.get(i - 1)));
        }
        double mean = logReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = logReturns.stream()
                .mapToDouble(r -> (r - mean) * (r - mean))
                .sum() / (logReturns.size() - 1);
        double annualized = Math.sqrt(variance) * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100.0;
        return BigDecimal.valueOf(annualized).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static RiskLevel riskLevel(BigDecimal volatilityPct) {
        if (volatilityPct == null) {
            return null;
        }
        if (volatilityPct.compareTo(RISK_LOW_MAX) < 0) {
            return RiskLevel.LOW;
        }
        if (volatilityPct.compareTo(RISK_MEDIUM_MAX) < 0) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }

    private List<CuratedAsset> buildUniverse() {
        List<CuratedAsset> universe = new ArrayList<>();
        for (var entry : TYPE_MAP.entrySet()) {
            TrackedAssetType trackedType = entry.getKey();
            AnalyticsInstrumentType analyticsType = entry.getValue();
            try {
                List<String> codes = trackedAssetQueryService.getEnabledCodes(trackedType);
                Map<String, String> names = trackedAssetQueryService.getDisplayNameMap(trackedType);
                for (String code : codes) {
                    universe.add(new CuratedAsset(analyticsType, code, names.getOrDefault(code, code)));
                }
            } catch (RuntimeException e) {
                log.warn("Failed to enumerate tracked type={}: {}", trackedType, e.getMessage());
            }
        }
        return universe;
    }

    private record CuratedAsset(AnalyticsInstrumentType type, String code, String name) {}
}
