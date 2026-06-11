package com.finance.market.macro.service;

import com.finance.market.macro.dto.InflationRate;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.repository.MacroIndicatorPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Derives headline inflation rates from a stored CPI/PPI series. Inflation indicators are persisted as a
 * cumulative INDEX level (2003=100 base) that only ever rises; what people read as "inflation" is the
 * index's rate of change. This service converts the index into year-over-year and month-over-month
 * percentage change ({@code latest / earlier - 1}) — the same way TÜİK computes the official rate — so a
 * falling rate (disinflation) is visible even while the underlying index keeps climbing.
 */
@Service
@RequiredArgsConstructor
public class MacroInflationRateService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RATIO_SCALE = 10;
    private static final int PCT_SCALE = 2;
    /** Monthly observations sit ~30 days apart; a 20-day window matches the right month without spilling over. */
    private static final long MATCH_TOLERANCE_DAYS = 20;

    private final MacroIndicatorPointRepository pointRepository;

    /**
     * Computes the year-over-year and month-over-month inflation for an indicator, or
     * {@link InflationRate#EMPTY} when it is not an index-based inflation series or has no cached latest value.
     */
    @Transactional(readOnly = true)
    public InflationRate compute(MacroIndicator indicator) {
        if (indicator.getCategory() != MacroCategory.INFLATION
                || indicator.getUnit() != MacroUnit.INDEX
                || indicator.getLastValue() == null
                || indicator.getLastDate() == null) {
            return InflationRate.EMPTY;
        }
        LocalDate last = indicator.getLastDate();
        List<MacroIndicatorPoint> priors = pointRepository
                .findByIndicatorAndObservedAtBetweenOrderByObservedAtAsc(
                        indicator, last.minusMonths(13), last.minusDays(1));
        BigDecimal yoy = pctChange(indicator.getLastValue(), valueNear(priors, last.minusMonths(12)));
        BigDecimal mom = pctChange(indicator.getLastValue(), valueNear(priors, last.minusMonths(1)));
        return new InflationRate(yoy, mom);
    }

    /** Percentage change of {@code latest} over {@code base}, or {@code null} when no usable base exists. */
    private static BigDecimal pctChange(BigDecimal latest, BigDecimal base) {
        if (base == null || base.signum() <= 0) {
            return null;
        }
        return latest.divide(base, RATIO_SCALE, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(HUNDRED)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    /** Value of the observation closest to {@code target}, or {@code null} when the nearest is out of tolerance. */
    private static BigDecimal valueNear(List<MacroIndicatorPoint> points, LocalDate target) {
        MacroIndicatorPoint best = null;
        long bestDiff = Long.MAX_VALUE;
        for (MacroIndicatorPoint point : points) {
            long diff = Math.abs(ChronoUnit.DAYS.between(point.getObservedAt(), target));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = point;
            }
        }
        return (best != null && bestDiff <= MATCH_TOLERANCE_DAYS) ? best.getValue() : null;
    }
}
