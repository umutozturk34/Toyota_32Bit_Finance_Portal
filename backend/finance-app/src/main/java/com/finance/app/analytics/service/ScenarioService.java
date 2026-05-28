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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class ScenarioService {

    private static final String CPI_CODE = "TP.TUFE1YI.T1";
    private static final int RETURN_SCALE = 4;
    private static final int VALUE_SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal SPLIT_DETECTION_HIGH = new BigDecimal("5");
    private static final BigDecimal SPLIT_DETECTION_LOW = new BigDecimal("0.2");
    private static final int BASELINE_PROBE_WINDOW = 10;

    private final UnifiedHistoryService historyService;
    private final MacroIndicatorQueryService macroQueryService;
    private final AnalyticsPriceSeriesProvider priceSeriesProvider;
    private final AnalyticsProperties analyticsProperties;

    public ScenarioResponse simulate(ScenarioRequest request) {
        LocalDate startDate = request.startDate();
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

    private boolean shouldCompound(AnalyticsInstrument instrument) {
        if (!instrument.type().isRateBacked()) return false;
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
        HistoryPoint baseline = raw.get(baselineIdx);
        BigDecimal basePrice = baseline.value();
        if (basePrice == null || basePrice.signum() <= 0) return emptySeries(instrument);

        BigDecimal baseFx = series.fxAt(baseline.date());
        if (baseFx == null) baseFx = series.baseFx();
        if (baseFx == null || baseFx.signum() <= 0) return emptySeries(instrument);

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

        BigDecimal finalValue = points.get(points.size() - 1).value();
        int threshold = analyticsProperties.scenario().partialThresholdDays();
        boolean endsLate = raw.get(raw.size() - 1).date().isBefore(endDate.minusDays(threshold));
        boolean startsLate = baseline.date().isAfter(startDate.plusDays(threshold));
        return buildSeries(instrument, points, finalValue, amount, cpiGrowthRatio, nativeCurrency,
                endsLate || startsLate);
    }

    private static int pickBaselineIndex(List<HistoryPoint> raw) {
        if (raw == null || raw.isEmpty()) return -1;
        if (raw.size() < 3) return 0;
        int scanLimit = Math.min(raw.size() - 1, Math.max(BASELINE_PROBE_WINDOW, raw.size() / 3));
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

    private ScenarioSeries simulateRate(AnalyticsInstrument instrument, PricedSeries series,
                                        BigDecimal amount, LocalDate startDate, LocalDate endDate,
                                        BigDecimal cpiGrowthRatio, Currency nativeCurrency) {
        List<HistoryPoint> raw = series.rawPoints();
        BigDecimal baseFx = series.baseFx();
        if (baseFx == null || baseFx.signum() <= 0) return emptySeries(instrument);

        List<ScenarioPoint> points = new ArrayList<>();
        points.add(new ScenarioPoint(startDate, amount));

        LocalDate firstObservation = raw.get(0).date();
        if (startDate.isBefore(firstObservation)) {
            points.add(new ScenarioPoint(firstObservation, amount));
        }

        BigDecimal compoundFactor = BigDecimal.ONE;
        LocalDate cursor = firstObservation.isAfter(startDate) ? firstObservation : startDate;

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

        if (points.size() <= 1) return emptySeries(instrument);
        int threshold = analyticsProperties.scenario().partialThresholdDays();
        boolean endsLate = cursor.isBefore(endDate.minusDays(threshold));
        boolean startsLate = firstObservation.isAfter(startDate.plusDays(threshold));
        BigDecimal finalValue = points.get(points.size() - 1).value();
        return buildSeries(instrument, points, finalValue, amount, cpiGrowthRatio, nativeCurrency,
                endsLate || startsLate);
    }

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

    private BigDecimal computeRealPct(BigDecimal finalValue, BigDecimal amount, BigDecimal cpiGrowthRatio) {
        if (finalValue == null || amount == null || amount.signum() == 0 || cpiGrowthRatio == null) return null;
        BigDecimal realFinal = finalValue.divide(cpiGrowthRatio, VALUE_SCALE, RoundingMode.HALF_UP);
        return realFinal.subtract(amount).multiply(HUNDRED)
                .divide(amount, RETURN_SCALE, RoundingMode.HALF_UP);
    }

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
