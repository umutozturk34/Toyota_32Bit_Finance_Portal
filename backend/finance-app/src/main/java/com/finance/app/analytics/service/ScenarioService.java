package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.ScenarioPoint;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.dto.response.ScenarioSeries;
import com.finance.app.config.AnalyticsProperties;
import com.finance.common.exception.BadRequestException;
import com.finance.common.model.Currency;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import com.finance.shared.util.ReturnMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * "Invest {@code amount} at {@code startDate}, track its value over time" engine for each requested
 * instrument, in a chosen target currency. Price-backed instruments follow the price path
 * (value = amount × price(t) × fx(t) / (basePrice × baseFx)); rate-backed ones (deposits/macro rates)
 * grow by daily compounding in their own currency and are re-converted to target at each day's FX.
 * Real returns are deflated by CPI growth over the window (TRY target only).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ScenarioService {

    // TP.GENENDEKS.T1 is the actual TÜFE (CPI) series — the misleading TCMB EVDS code naming
    // makes TP.TUFE1YI.T1 look like CPI but it is Yİ-ÜFE (PPI). Real-return deflation needs CPI.
    private static final String CPI_CODE = "TP.GENENDEKS.T1";
    private static final int RETURN_SCALE = 4;
    private static final int VALUE_SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal SPLIT_DETECTION_HIGH = new BigDecimal("5");
    private static final BigDecimal SPLIT_DETECTION_LOW = new BigDecimal("0.2");
    private static final int BASELINE_PROBE_WINDOW = 10;
    // Earliest scenario start: first trading day of 2000, the floor of EUR/TRY FX history (mirrors the
    // portfolio lot floor). Before this there is no EUR rate to value a multi-currency basket against.
    private static final LocalDate EARLIEST_START = LocalDate.of(2000, 1, 4);

    private final UnifiedHistoryService historyService;
    private final MacroIndicatorQueryService macroQueryService;
    private final AnalyticsPriceSeriesProvider priceSeriesProvider;
    private final AnalyticsProperties analyticsProperties;

    /**
     * Simulates every requested instrument over {@code [startDate, endDate]} (end defaults to today).
     *
     * @throws BadRequestException if the date range is non-positive
     */
    public ScenarioResponse simulate(ScenarioRequest request) {
        LocalDate startDate = request.startDate();
        if (startDate.isBefore(EARLIEST_START)) {
            throw new BadRequestException("error.analytics.startDateTooOld");
        }
        LocalDate endDate = request.endDate() != null ? request.endDate() : LocalDate.now();
        if (!endDate.isAfter(startDate)) {
            throw new BadRequestException("error.analytics.invalidDateRange");
        }

        Currency target = request.targetCurrency() != null ? request.targetCurrency() : Currency.TRY;

        log.debug("Simulating scenario amount={} window={}..{} instruments={} target={}",
                request.amount(), startDate, endDate, request.instruments().size(), target);

        BigDecimal cpiGrowthRatio = target == Currency.TRY
                ? computeCpiGrowthRatio(startDate, endDate)
                : null;
        BigDecimal cpiGrowthPct = cpiGrowthRatio != null
                ? cpiGrowthRatio.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(RETURN_SCALE, RoundingMode.HALF_UP)
                : null;

        List<ScenarioSeries> series = request.instruments().stream()
                .map(instrument -> simulateOne(instrument, request.amount(), startDate, endDate,
                        cpiGrowthRatio, target))
                .toList();

        log.info("Scenario computed window={}..{} target={} cpiGrowth={}% series={}",
                startDate, endDate, target, cpiGrowthPct, series.size());
        return new ScenarioResponse(request.amount(), startDate, endDate, cpiGrowthPct, target, series);
    }

    private ScenarioSeries simulateOne(AnalyticsInstrument instrument, BigDecimal amount,
                                       LocalDate startDate, LocalDate endDate,
                                       BigDecimal cpiGrowthRatio, Currency target) {
        PricedSeries series = priceSeriesProvider.fetch(instrument, startDate, endDate, target);
        if (series.isEmpty()) {
            return emptySeries(instrument);
        }
        return shouldCompound(instrument)
                ? simulateRate(instrument, series, amount, startDate, endDate, cpiGrowthRatio, series.nativeCurrency())
                : simulateMarket(instrument, series, amount, startDate, endDate, cpiGrowthRatio, series.nativeCurrency());
    }

    /**
     * Rate-backed instruments compound; macro/deposit codes only compound when expressed as a PERCENT
     * unit (otherwise they are index-like and follow the price path). Defaults to compounding if the
     * unit can't be resolved.
     */
    private boolean shouldCompound(AnalyticsInstrument instrument) {
        if (!instrument.type().isRateBacked()) return false;
        // BOND is rate-backed but its series is coupon-YIELD history, not a deposit interest rate:
        // compounding it would fabricate a deposit-like growth index from yields. Bond yields are rate
        // levels (never compounded, never FX-converted — same as Compare's isRateLike path), so route
        // BOND down the level path instead. The scenario UI already hides BOND; this guards a direct API call.
        if (instrument.type() == AnalyticsInstrumentType.BOND) return false;
        if (instrument.type() == AnalyticsInstrumentType.MACRO
                || instrument.type() == AnalyticsInstrumentType.DEPOSIT) {
            try {
                MacroIndicator ind = macroQueryService.findByCode(instrument.code());
                return ind.getUnit() == MacroUnit.PERCENT;
            } catch (Exception e) {
                log.warn("Cannot resolve macro unit code={}, defaulting to compound: {}",
                        instrument.code(), e.getMessage());
                return true;
            }
        }
        return true;
    }

    private ScenarioSeries simulateMarket(AnalyticsInstrument instrument, PricedSeries series,
                                          BigDecimal amount, LocalDate startDate, LocalDate endDate,
                                          BigDecimal cpiGrowthRatio, Currency nativeCurrency) {
        List<HistoryPoint> raw = series.rawPoints();
        int baselineIdx = pickBaselineIndex(raw);
        if (baselineIdx < 0) return emptySeries(instrument);
        // Advance to the first post-split point with BOTH a usable price and its OWN-date FX, so the price
        // denominator (basePrice) and FX denominator (baseFx) share one anchor date. The old baseFx()
        // fallback anchored FX at raw.get(0) while basePrice sat at a later split-adjusted baseline, scaling
        // the whole USD/EUR level by fx(baseline)/fx(raw0). For native==target, fxAt is 1 on every date so
        // the first valid point is the baseline (no-op).
        HistoryPoint baseline = null;
        BigDecimal basePrice = null;
        BigDecimal baseFx = null;
        for (int i = baselineIdx; i < raw.size(); i++) {
            HistoryPoint p = raw.get(i);
            if (p.value() == null || p.value().signum() <= 0) continue;
            BigDecimal fx = series.fxAt(p.date());
            if (fx == null || fx.signum() <= 0) continue;
            baseline = p;
            basePrice = p.value();
            baseFx = fx;
            baselineIdx = i;
            break;
        }
        if (baseline == null) return emptySeries(instrument);

        List<ScenarioPoint> points = new ArrayList<>(raw.size() - baselineIdx);
        for (int i = baselineIdx; i < raw.size(); i++) {
            HistoryPoint p = raw.get(i);
            if (p.value() == null) continue;
            BigDecimal fxAtPoint = series.fxAt(p.date());
            if (fxAtPoint == null) continue;
            BigDecimal valueTarget = amount
                    .multiply(p.value())
                    .multiply(fxAtPoint)
                    .divide(basePrice.multiply(baseFx), VALUE_SCALE, RoundingMode.HALF_UP);
            points.add(new ScenarioPoint(p.date(), valueTarget));
        }
        if (points.isEmpty()) return emptySeries(instrument);

        ScenarioPoint lastPlotted = points.get(points.size() - 1);
        BigDecimal finalValue = lastPlotted.value();
        int threshold = analyticsProperties.scenario().partialThresholdDays();
        // endsLate must reflect the last point ACTUALLY plotted (the date finalValue is measured at), not
        // the last raw observation: trailing points can be dropped when their FX is missing (e.g. forex
        // candles lag a USD-native equity for recent days), leaving finalValue short of the window end. Using
        // raw.last would then mark a truncated series as non-partial. Mirrors simulateRate's cursor check.
        boolean endsLate = lastPlotted.date().isBefore(endDate.minusDays(threshold));
        boolean startsLate = baseline.date().isAfter(startDate.plusDays(threshold));
        return buildSeries(instrument, points, finalValue, amount, cpiGrowthRatio, nativeCurrency,
                endsLate || startsLate);
    }

    /**
     * Finds the baseline (start) index for the price path, skipping past a LEADING stock-split-like jump
     * (ratio &gt;5 or &lt;0.2 within only the first {@link #BASELINE_PROBE_WINDOW} points) so an unadjusted
     * split or launch-week artifact at the very start doesn't distort the simulation. The probe is
     * deliberately leading-only: a large jump DEEPER in the window is a real move (e.g. ISATR, İş Bankası's
     * thinly-traded A-class share, genuinely re-pricing ~88x) and must be kept — skipping it would push the
     * baseline months in and wrongly drop a full-history asset as "partial". Returns the index after the last
     * leading jump, else 0.
     */
    private static int pickBaselineIndex(List<HistoryPoint> raw) {
        if (raw == null || raw.isEmpty()) return -1;
        if (raw.size() < 3) return 0;
        int scanLimit = Math.min(raw.size() - 1, BASELINE_PROBE_WINDOW);
        int jumpIdx = -1;
        for (int i = 0; i < scanLimit; i++) {
            BigDecimal cur = raw.get(i).value();
            BigDecimal next = raw.get(i + 1).value();
            if (cur == null || next == null || cur.signum() <= 0 || next.signum() <= 0) continue;
            BigDecimal ratio = next.divide(cur, 6, RoundingMode.HALF_UP);
            if (ratio.compareTo(SPLIT_DETECTION_HIGH) > 0
                    || ratio.compareTo(SPLIT_DETECTION_LOW) < 0) {
                jumpIdx = i + 1;
            }
        }
        return jumpIdx >= 0 ? jumpIdx : 0;
    }

    /**
     * Deposit/rate path: converts the principal to the deposit's own currency at entry, compounds it
     * piecewise over each observation interval (applying that interval's earlier-endpoint annual rate
     * for its day count), and re-converts to the target currency at each day's FX.
     */
    private ScenarioSeries simulateRate(AnalyticsInstrument instrument, PricedSeries series,
                                        BigDecimal amount, LocalDate startDate, LocalDate endDate,
                                        BigDecimal cpiGrowthRatio, Currency nativeCurrency) {
        List<HistoryPoint> raw = series.rawPoints();
        // Anchor the FX round-trip to the scenario start (where the principal is placed), not the first
        // rate observation: deposit rates are published periodically, so raw.get(0) can sit days/weeks
        // after startDate — anchoring there converts the principal at the wrong day's FX. The provider
        // seeds fxAt(start); fall back to the first-observation FX only if start has no rate.
        BigDecimal baseFx = series.fxAt(startDate);
        if (baseFx == null || baseFx.signum() <= 0) baseFx = series.baseFx();
        if (baseFx == null || baseFx.signum() <= 0) return emptySeries(instrument);

        List<ScenarioPoint> points = new ArrayList<>();
        points.add(new ScenarioPoint(startDate, amount));

        LocalDate firstObservation = raw.get(0).date();

        // Compound from the SELECTED start date itself — base = amount at startDate, then it grows from day
        // one. The first published rate is carried back over the short [startDate, firstObservation] lead-in
        // (deposit rates barely move week to week) instead of sitting flat with no interest. The old flat
        // lead-in made the Beater earn 0 over that gap and read ~0.4pt LOWER than Compare, which accrues from
        // the window start; both now compound from startDate so the numbers reconcile.
        BigDecimal compoundFactor = BigDecimal.ONE;
        LocalDate cursor = startDate;

        for (int i = 0; i + 1 < raw.size(); i++) {
            BigDecimal annualRatePct = raw.get(i).value();
            if (annualRatePct == null) continue;

            LocalDate stepEnd = raw.get(i + 1).date();
            long days = ChronoUnit.DAYS.between(cursor, stepEnd);
            if (days <= 0) continue;

            compoundFactor = applyCompound(compoundFactor, annualRatePct, days);
            cursor = stepEnd;
            BigDecimal fxAtPoint = series.fxAt(stepEnd);
            if (fxAtPoint == null) continue;
            BigDecimal valueTarget = amount
                    .multiply(compoundFactor)
                    .multiply(fxAtPoint)
                    .divide(baseFx, VALUE_SCALE, RoundingMode.HALF_UP);
            points.add(new ScenarioPoint(stepEnd, valueTarget));
        }

        // Carry the most recent published rate forward to the scenario end date: a deposit keeps
        // accruing interest at its last-known rate until a newer rate is published, so the series must
        // reach endDate rather than stopping at the final observation — otherwise the trailing publication
        // gap (EVDS lag) silently drops days of interest and the line flat-lines / disappears early.
        if (cursor.isBefore(endDate) && !raw.isEmpty()) {
            BigDecimal lastRate = raw.get(raw.size() - 1).value();
            long tailDays = ChronoUnit.DAYS.between(cursor, endDate);
            BigDecimal fxAtEnd = series.fxAt(endDate);
            if (lastRate != null && tailDays > 0 && fxAtEnd != null) {
                compoundFactor = applyCompound(compoundFactor, lastRate, tailDays);
                BigDecimal valueTarget = amount
                        .multiply(compoundFactor)
                        .multiply(fxAtEnd)
                        .divide(baseFx, VALUE_SCALE, RoundingMode.HALF_UP);
                points.add(new ScenarioPoint(endDate, valueTarget));
                cursor = endDate;
            }
        }

        if (points.size() <= 1) return emptySeries(instrument);
        int threshold = analyticsProperties.scenario().partialThresholdDays();
        boolean endsLate = cursor.isBefore(endDate.minusDays(threshold));
        boolean startsLate = firstObservation.isAfter(startDate.plusDays(threshold));
        BigDecimal finalValue = points.get(points.size() - 1).value();
        return buildSeries(instrument, points, finalValue, amount, cpiGrowthRatio, nativeCurrency,
                endsLate || startsLate);
    }

    /** Grows {@code value} by daily compounding: factor × (1 + annualRate/100/365)^days. */
    private BigDecimal applyCompound(BigDecimal value, BigDecimal annualRatePct, long days) {
        BigDecimal dailyRate = annualRatePct.divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .divide(DAYS_PER_YEAR, 12, RoundingMode.HALF_UP);
        double factor = Math.pow(1.0 + dailyRate.doubleValue(), days);
        return value.multiply(BigDecimal.valueOf(factor)).setScale(VALUE_SCALE, RoundingMode.HALF_UP);
    }

    private ScenarioSeries buildSeries(AnalyticsInstrument instrument, List<ScenarioPoint> points,
                                       BigDecimal finalValue, BigDecimal amount,
                                       BigDecimal cpiGrowthRatio, Currency nativeCurrency, boolean partial) {
        BigDecimal nominalPct = computeNominalPct(finalValue, amount);
        BigDecimal realPct = computeRealPct(finalValue, amount, cpiGrowthRatio);
        return new ScenarioSeries(instrument, points, finalValue, nominalPct, realPct, nativeCurrency, partial);
    }

    private ScenarioSeries emptySeries(AnalyticsInstrument instrument) {
        return new ScenarioSeries(instrument, List.of(), null, null, null, null, true);
    }

    private BigDecimal computeNominalPct(BigDecimal finalValue, BigDecimal amount) {
        if (finalValue == null || amount == null || amount.signum() == 0) return null;
        return finalValue.subtract(amount).multiply(HUNDRED)
                .divide(amount, RETURN_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Real return: the geometric (Fisher) excess of the nominal return over CPI growth, via the shared
     * {@link ReturnMath#realExcessPct} — the same primitive the inflation-beater uses for its excess. Null
     * when CPI is unavailable or the nominal is incomputable (null amount/value or zero amount).
     */
    private BigDecimal computeRealPct(BigDecimal finalValue, BigDecimal amount, BigDecimal cpiGrowthRatio) {
        if (cpiGrowthRatio == null) return null;
        BigDecimal cpiGrowthPct = cpiGrowthRatio.subtract(BigDecimal.ONE).multiply(HUNDRED);
        return ReturnMath.realExcessPct(computeNominalPct(finalValue, amount), cpiGrowthPct, RETURN_SCALE);
    }

    /**
     * CPI growth ratio (cpiEnd/cpiStart) across the window, using the closest CPI observation at or
     * before each endpoint (probing a couple of months earlier since CPI is monthly). Null when the
     * series is too sparse to compute.
     */
    private BigDecimal computeCpiGrowthRatio(LocalDate startDate, LocalDate endDate) {
        List<HistoryPoint> cpi = historyService.getMacroSeries(CPI_CODE, startDate.minusMonths(2), endDate.plusMonths(1));
        if (cpi.size() < 2) {
            log.warn("CPI series insufficient code={} window={}..{} points={}",
                    CPI_CODE, startDate, endDate, cpi.size());
            return null;
        }
        HistoryPoint baseline = null;
        HistoryPoint latest = null;
        for (HistoryPoint p : cpi) {
            if (p.date() == null || p.value() == null) continue;
            if (!p.date().isAfter(endDate) && (latest == null || p.date().isAfter(latest.date()))) {
                latest = p;
            }
            if (!p.date().isAfter(startDate) && (baseline == null || p.date().isAfter(baseline.date()))) {
                baseline = p;
            }
        }
        if (baseline == null) {
            for (HistoryPoint p : cpi) {
                if (p.date() == null || p.value() == null) continue;
                if (!p.date().isBefore(startDate) && (baseline == null || p.date().isBefore(baseline.date()))) {
                    baseline = p;
                }
            }
        }
        if (baseline == null || latest == null) {
            log.warn("CPI values insufficient for window {}..{}", startDate, endDate);
            return null;
        }
        BigDecimal cpiStart = baseline.value();
        BigDecimal cpiEnd = latest.value();
        if (cpiStart.signum() <= 0) {
            log.warn("CPI baseline non-positive for window {}..{}", startDate, endDate);
            return null;
        }
        return cpiEnd.divide(cpiStart, 10, RoundingMode.HALF_UP);
    }
}
