package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.response.ScenarioPoint;
import com.finance.app.analytics.dto.response.ScenarioSeries;
import com.finance.app.config.AnalyticsProperties;
import com.finance.common.model.Currency;
import com.finance.shared.util.ReturnMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-instrument-type value-path strategies for the scenario engine: given an invested {@code amount}
 * and a window, each method walks one kind of instrument's history to a target-currency value series
 * (index-level macro, price-backed market asset, compounding rate/deposit). These three paths share the
 * same series-assembly and nominal/real-return math, so they live together here behind a stateless
 * collaborator; {@link ScenarioService} stays the orchestrator that resolves instrument type, fetches the
 * priced series, and dispatches to the matching path. Splitting along this boundary keeps the bulk of the
 * value-path arithmetic in one cohesive unit without inflating the orchestrator.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ScenarioSimulationEngine {

    private static final int RETURN_SCALE = 4;
    private static final int VALUE_SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal SPLIT_DETECTION_HIGH = new BigDecimal("5");
    private static final BigDecimal SPLIT_DETECTION_LOW = new BigDecimal("0.2");
    private static final int BASELINE_PROBE_WINDOW = 10;

    private final AnalyticsPriceSeriesProvider priceSeriesProvider;
    private final AnalyticsProperties analyticsProperties;

    /**
     * Index-level macro path (CPI/PPI): the value of {@code amount} riding the index from the start. Unlike
     * {@link #simulateMarket} it anchors the baseline to the most recent observation AT OR BEFORE the start
     * (fetching a two-month lead-in to reach it), matching the CPI deflator. A plain in-window fetch starts
     * at the first print AFTER the start and drops the first month's inflation — exactly why the inflation
     * instrument used to read a small negative real return ("losing to inflation"). Ends at the last print
     * on/before the window end (no extrapolation) so it reconciles with the deflator to the cent. FX framing
     * mirrors the market path for non-TRY targets.
     */
    public ScenarioSeries simulateIndexMacro(AnalyticsInstrument instrument, BigDecimal amount,
                                             LocalDate startDate, LocalDate endDate,
                                             BigDecimal cpiGrowthRatio, Currency target) {
        PricedSeries series = priceSeriesProvider.fetch(instrument, startDate.minusMonths(2), endDate, target);
        if (series.isEmpty()) {
            return emptySeries(instrument);
        }
        List<HistoryPoint> raw = series.rawPoints();

        HistoryPoint baseline = null;
        for (HistoryPoint p : raw) {
            if (p.value() == null || p.value().signum() <= 0) {
                continue;
            }
            if (p.date().isAfter(startDate)) {
                if (baseline == null) {
                    baseline = p; // series starts mid-window: fall back to its first print
                }
                break;
            }
            baseline = p; // latest valid print on/before the start
        }
        if (baseline == null) {
            return emptySeries(instrument);
        }
        BigDecimal basePrice = baseline.value();
        BigDecimal baseFx = series.fxAt(baseline.date());
        if (baseFx == null || baseFx.signum() <= 0) {
            baseFx = series.baseFx();
        }
        if (baseFx == null || baseFx.signum() <= 0) {
            return emptySeries(instrument);
        }

        List<ScenarioPoint> points = new ArrayList<>();
        points.add(new ScenarioPoint(startDate, amount));
        BigDecimal lastIndexValue = null;
        for (HistoryPoint p : raw) {
            if (p.value() == null || !p.date().isAfter(startDate) || p.date().isAfter(endDate)) {
                continue;
            }
            BigDecimal fx = series.fxAt(p.date());
            if (fx == null) {
                continue;
            }
            BigDecimal value = amount.multiply(p.value()).multiply(fx)
                    .divide(basePrice.multiply(baseFx), VALUE_SCALE, RoundingMode.HALF_UP);
            points.add(new ScenarioPoint(p.date(), value));
            lastIndexValue = p.value();
        }
        // Carry the last published index level flat to the scenario end date: a CPI/PPI level does not change
        // until the next monthly print, so the plotted line must reach endDate rather than stopping weeks short
        // at the final EVDS observation (publication lag). Only the FX leg moves over the gap; mirrors the rate
        // path's tail-carry so the inflation line ends level with the deposit lines instead of cutting off early.
        if (lastIndexValue != null && points.get(points.size() - 1).date().isBefore(endDate)) {
            BigDecimal fxAtEnd = series.fxAt(endDate);
            if (fxAtEnd == null || fxAtEnd.signum() <= 0) {
                fxAtEnd = baseFx;
            }
            BigDecimal endValue = amount.multiply(lastIndexValue).multiply(fxAtEnd)
                    .divide(basePrice.multiply(baseFx), VALUE_SCALE, RoundingMode.HALF_UP);
            points.add(new ScenarioPoint(endDate, endValue));
        }
        if (points.size() <= 1) {
            return emptySeries(instrument);
        }
        BigDecimal finalValue = points.get(points.size() - 1).value();
        // Monthly index data legitimately lags the window end by a few weeks; that trailing gap is not a
        // truncation, so only a baseline starting AFTER the window start (a series younger than the window)
        // marks the run partial.
        boolean partial = baseline.date().isAfter(startDate);
        return buildSeries(instrument, points, finalValue, amount, cpiGrowthRatio, series.nativeCurrency(), partial);
    }

    /**
     * Price-backed market path: the value of {@code amount} riding the instrument's price level, re-expressed
     * in the target currency at each day's FX (value = amount × price(t) × fx(t) / (basePrice × baseFx)).
     */
    public ScenarioSeries simulateMarket(AnalyticsInstrument instrument, PricedSeries series,
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
        // baseline, basePrice and baseFx are assigned together in the loop above, so they are all null or all
        // non-null; checking the latter two as well makes that invariant explicit for static analysis (and
        // guarantees the basePrice.multiply(baseFx) denominator below is non-null).
        if (baseline == null || basePrice == null || baseFx == null) return emptySeries(instrument);

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
    public ScenarioSeries simulateRate(AnalyticsInstrument instrument, PricedSeries series,
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

    /** Assembles a populated series, attaching the nominal and (CPI-deflated) real return for the run. */
    public ScenarioSeries buildSeries(AnalyticsInstrument instrument, List<ScenarioPoint> points,
                                      BigDecimal finalValue, BigDecimal amount,
                                      BigDecimal cpiGrowthRatio, Currency nativeCurrency, boolean partial) {
        BigDecimal nominalPct = computeNominalPct(finalValue, amount);
        BigDecimal realPct = computeRealPct(finalValue, amount, cpiGrowthRatio);
        return new ScenarioSeries(instrument, points, finalValue, nominalPct, realPct, nativeCurrency, partial);
    }

    /** Empty/partial placeholder used whenever an instrument has no usable history in the window. */
    public ScenarioSeries emptySeries(AnalyticsInstrument instrument) {
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
}
