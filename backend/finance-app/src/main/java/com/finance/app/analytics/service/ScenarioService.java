package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.dto.response.ScenarioSeries;
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
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    // Earliest scenario start: first trading day of 2000, the floor of EUR/TRY FX history (mirrors the
    // portfolio lot floor). Before this there is no EUR rate to value a multi-currency basket against.
    private static final LocalDate EARLIEST_START = LocalDate.of(2000, 1, 4);

    private final UnifiedHistoryService historyService;
    private final MacroIndicatorQueryService macroQueryService;
    private final AnalyticsPriceSeriesProvider priceSeriesProvider;
    private final ScenarioSimulationEngine simulationEngine;

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

        // Bonds are excluded from scenario analytics: their bond_rate_history is coupon-YIELD, not a value path
        // that scales cleanly with an invested amount. The UI already hides BOND from the picker; filtering here
        // is the backend guard so a stale recent-search or a direct API call can't slip a bond into the run.
        List<ScenarioSeries> series = request.instruments().stream()
                .filter(instrument -> instrument.type() != AnalyticsInstrumentType.BOND)
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
        // A macro INDEX instrument (CPI/PPI) is anchored to the most recent print AT OR BEFORE the start —
        // the same convention the CPI deflator uses — so "investing in inflation" earns exactly the deflator
        // and reads ~0 real return, instead of losing to inflation by the one month a plain in-window fetch
        // silently drops. Handled before the generic fetch because it needs a pre-start lead-in.
        if (isIndexMacro(instrument)) {
            return simulationEngine.simulateIndexMacro(instrument, amount, startDate, endDate, cpiGrowthRatio, target);
        }
        PricedSeries series = priceSeriesProvider.fetch(instrument, startDate, endDate, target);
        if (series.isEmpty()) {
            return simulationEngine.emptySeries(instrument);
        }
        return shouldCompound(instrument)
                ? simulationEngine.simulateRate(instrument, series, amount, startDate, endDate, cpiGrowthRatio, series.nativeCurrency())
                : simulationEngine.simulateMarket(instrument, series, amount, startDate, endDate, cpiGrowthRatio, series.nativeCurrency());
    }

    /** True for a macro/deposit indicator quoted as an INDEX/NUMBER level (CPI, PPI) rather than a PERCENT rate. */
    private boolean isIndexMacro(AnalyticsInstrument instrument) {
        if (instrument.type() != AnalyticsInstrumentType.MACRO
                && instrument.type() != AnalyticsInstrumentType.DEPOSIT) {
            return false;
        }
        try {
            MacroUnit unit = macroQueryService.findByCode(instrument.code()).getUnit();
            return unit == MacroUnit.INDEX || unit == MacroUnit.NUMBER;
        } catch (Exception e) {
            return false;
        }
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
