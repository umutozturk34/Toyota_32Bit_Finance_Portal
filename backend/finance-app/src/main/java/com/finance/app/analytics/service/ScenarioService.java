package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.ScenarioPoint;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.dto.response.ScenarioSeries;
import com.finance.common.exception.BadRequestException;
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

    private final UnifiedHistoryService historyService;
    private final MacroIndicatorQueryService macroQueryService;

    public ScenarioResponse simulate(ScenarioRequest request) {
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate() != null ? request.endDate() : LocalDate.now();
        if (!endDate.isAfter(startDate)) {
            throw new BadRequestException("error.analytics.invalidDateRange");
        }

        log.debug("Simulating scenario amount={} window={}..{} instruments={}",
                request.amount(), startDate, endDate, request.instruments().size());

        BigDecimal cpiGrowthRatio = computeCpiGrowthRatio(startDate, endDate);
        BigDecimal cpiGrowthPct = cpiGrowthRatio != null
                ? cpiGrowthRatio.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(RETURN_SCALE, RoundingMode.HALF_UP)
                : null;

        List<ScenarioSeries> series = request.instruments().stream()
                .map(instrument -> simulateOne(instrument, request.amount(), startDate, endDate, cpiGrowthRatio))
                .toList();

        log.info("Scenario computed window={}..{} cpiGrowth={}% series={}",
                startDate, endDate, cpiGrowthPct, series.size());
        return new ScenarioResponse(request.amount(), startDate, endDate, cpiGrowthPct, series);
    }

    private ScenarioSeries simulateOne(AnalyticsInstrument instrument, BigDecimal amount,
                                       LocalDate startDate, LocalDate endDate, BigDecimal cpiGrowthRatio) {
        List<HistoryPoint> raw = historyService.getSeries(instrument, startDate, endDate);
        if (raw.isEmpty()) {
            return emptySeries(instrument);
        }
        return shouldCompound(instrument)
                ? simulateRate(instrument, raw, amount, startDate, endDate, cpiGrowthRatio)
                : simulateMarket(instrument, raw, amount, endDate, cpiGrowthRatio);
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

    private ScenarioSeries simulateMarket(AnalyticsInstrument instrument, List<HistoryPoint> raw,
                                          BigDecimal amount, LocalDate endDate, BigDecimal cpiGrowthRatio) {
        BigDecimal basePrice = raw.get(0).value();
        if (basePrice == null || basePrice.signum() <= 0) return emptySeries(instrument);

        List<ScenarioPoint> points = raw.stream()
                .map(p -> new ScenarioPoint(p.date(),
                        amount.multiply(p.value()).divide(basePrice, VALUE_SCALE, RoundingMode.HALF_UP)))
                .toList();

        BigDecimal finalValue = points.get(points.size() - 1).value();
        boolean partial = raw.get(raw.size() - 1).date().isBefore(endDate.minusDays(7));
        return buildSeries(instrument, points, finalValue, amount, cpiGrowthRatio, partial);
    }

    private ScenarioSeries simulateRate(AnalyticsInstrument instrument, List<HistoryPoint> raw,
                                        BigDecimal amount, LocalDate startDate, LocalDate endDate,
                                        BigDecimal cpiGrowthRatio) {
        List<ScenarioPoint> points = new ArrayList<>();
        points.add(new ScenarioPoint(startDate, amount));

        BigDecimal currentValue = amount;
        LocalDate cursor = startDate;

        for (int i = 0; i < raw.size(); i++) {
            BigDecimal annualRatePct = raw.get(i).value();
            if (annualRatePct == null) continue;

            LocalDate stepEnd = (i + 1 < raw.size()) ? raw.get(i + 1).date() : endDate;
            long days = ChronoUnit.DAYS.between(cursor, stepEnd);
            if (days <= 0) continue;

            currentValue = applyCompound(currentValue, annualRatePct, days);
            cursor = stepEnd;
            points.add(new ScenarioPoint(stepEnd, currentValue));
        }

        boolean partial = cursor.isBefore(endDate.minusDays(7));
        return buildSeries(instrument, points, currentValue, amount, cpiGrowthRatio, partial);
    }

    private BigDecimal applyCompound(BigDecimal value, BigDecimal annualRatePct, long days) {
        BigDecimal dailyRate = annualRatePct.divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .divide(DAYS_PER_YEAR, 12, RoundingMode.HALF_UP);
        double factor = Math.pow(1.0 + dailyRate.doubleValue(), days);
        return value.multiply(BigDecimal.valueOf(factor)).setScale(VALUE_SCALE, RoundingMode.HALF_UP);
    }

    private ScenarioSeries buildSeries(AnalyticsInstrument instrument, List<ScenarioPoint> points,
                                       BigDecimal finalValue, BigDecimal amount,
                                       BigDecimal cpiGrowthRatio, boolean partial) {
        BigDecimal nominalPct = computeNominalPct(finalValue, amount);
        BigDecimal realPct = computeRealPct(finalValue, amount, cpiGrowthRatio);
        return new ScenarioSeries(instrument, points, finalValue, nominalPct, realPct, partial);
    }

    private ScenarioSeries emptySeries(AnalyticsInstrument instrument) {
        return new ScenarioSeries(instrument, List.of(), null, null, null, true);
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
        BigDecimal cpiStart = cpi.get(0).value();
        BigDecimal cpiEnd = cpi.get(cpi.size() - 1).value();
        if (cpiStart == null || cpiStart.signum() <= 0 || cpiEnd == null) {
            log.warn("CPI values invalid for window {}..{} (start={}, end={})",
                    startDate, endDate, cpiStart, cpiEnd);
            return null;
        }
        return cpiEnd.divide(cpiStart, 10, RoundingMode.HALF_UP);
    }
}
