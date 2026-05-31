package com.finance.portfolio.service;

import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Computes inflation-adjusted (real) returns by deflating each lot's entry cost to today's purchasing
 * power using the TÜİK CPI macro indicator. Each entry cost is scaled by {@code cpiLatest / cpiAtEntry}
 * to form a "real capital base"; the real PnL/percent is the current value measured against that base.
 * Returns {@link RealReturnSummary#EMPTY} when CPI data is insufficient.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class RealReturnCalculator {

    private static final String CPI_CODE = "TP.TUFE1YI.T1";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int RATIO_SCALE = 12;

    private final MacroIndicatorQueryService macroQueryService;

    /** Portfolio-level real return: inflation-adjusted PnL and percent, plus CPI growth since the earliest entry. */
    public record RealReturnSummary(
            BigDecimal realPnlTry,
            BigDecimal realPnlPercent,
            BigDecimal cpiGrowthPercent) {
        public static final RealReturnSummary EMPTY = new RealReturnSummary(null, null, null);
    }

    /** Per-position real-return percent keyed by position id; empty map when CPI data is missing or inputs are empty. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> computePerPosition(List<PortfolioPosition> positions,
                                                    Map<Long, BigDecimal> currentValues) {
        if (positions == null || positions.isEmpty() || currentValues == null || currentValues.isEmpty()) {
            return Map.of();
        }
        LocalDate earliest = earliestEntryDate(positions);
        if (earliest == null) return Map.of();
        NavigableMap<LocalDate, BigDecimal> cpiByDate = loadCpi(earliest);
        if (cpiByDate.size() < 2) return Map.of();
        BigDecimal cpiLatest = cpiByDate.lastEntry().getValue();

        Map<Long, BigDecimal> result = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            if (pos.getId() == null || pos.getEntryDate() == null) continue;
            BigDecimal entryValue = pos.entryValue();
            BigDecimal currentValue = currentValues.get(pos.getId());
            if (entryValue == null || currentValue == null || entryValue.signum() <= 0) continue;
            BigDecimal cpiAtEntry = cpiOnOrBefore(cpiByDate, pos.getEntryDate().toLocalDate());
            if (cpiAtEntry == null || cpiAtEntry.signum() <= 0) continue;
            BigDecimal deflator = cpiLatest.divide(cpiAtEntry, RATIO_SCALE, RoundingMode.HALF_UP);
            BigDecimal realBase = entryValue.multiply(deflator);
            if (realBase.signum() <= 0) continue;
            BigDecimal realPct = currentValue.subtract(realBase).multiply(HUNDRED)
                    .divide(realBase, MoneyScale.PRICE, RoundingMode.HALF_UP);
            result.put(pos.getId(), realPct);
        }
        return result;
    }

    /** Portfolio real return: {@code totalValueTry} measured against the CPI-deflated capital base of all lots. */
    @Transactional(readOnly = true)
    public RealReturnSummary compute(List<PortfolioPosition> positions, BigDecimal totalValueTry) {
        if (positions == null || positions.isEmpty() || totalValueTry == null) {
            return RealReturnSummary.EMPTY;
        }
        LocalDate earliest = earliestEntryDate(positions);
        if (earliest == null) return RealReturnSummary.EMPTY;

        NavigableMap<LocalDate, BigDecimal> cpiByDate = loadCpi(earliest);
        if (cpiByDate.size() < 2) {
            log.warn("CPI data insufficient earliest={} points={}", earliest, cpiByDate.size());
            return RealReturnSummary.EMPTY;
        }

        BigDecimal cpiLatest = cpiByDate.lastEntry().getValue();
        BigDecimal realCapitalBase = sumRealCapitalBase(positions, cpiByDate, cpiLatest);
        if (realCapitalBase == null || realCapitalBase.signum() <= 0) return RealReturnSummary.EMPTY;

        BigDecimal realPnl = totalValueTry.subtract(realCapitalBase).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal realPct = realPnl.multiply(HUNDRED).divide(realCapitalBase, MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal cpiGrowthPct = cpiGrowthPercent(cpiByDate, earliest, cpiLatest);

        log.debug("Real return computed positions={} earliest={} realPnl={} realPct={} cpiGrowthPct={}",
                positions.size(), earliest, realPnl, realPct, cpiGrowthPct);
        return new RealReturnSummary(realPnl, realPct, cpiGrowthPct);
    }

    private LocalDate earliestEntryDate(List<PortfolioPosition> positions) {
        LocalDate earliest = null;
        for (PortfolioPosition pos : positions) {
            if (pos.getEntryDate() == null) continue;
            LocalDate d = pos.getEntryDate().toLocalDate();
            if (earliest == null || d.isBefore(earliest)) earliest = d;
        }
        return earliest;
    }

    private NavigableMap<LocalDate, BigDecimal> loadCpi(LocalDate earliest) {
        try {
            MacroIndicator indicator = macroQueryService.findByCode(CPI_CODE);
            List<MacroIndicatorPoint> points = macroQueryService.history(
                    indicator, earliest.minusMonths(2), LocalDate.now());
            TreeMap<LocalDate, BigDecimal> map = new TreeMap<>();
            for (MacroIndicatorPoint p : points) {
                if (p.getValue() != null && p.getValue().signum() > 0) {
                    map.put(p.getObservedAt(), p.getValue());
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("CPI lookup failed for real return calc: {}", e.getMessage());
            return new TreeMap<>();
        }
    }

    /**
     * Sums each position's CPI-deflated entry value. Returns {@code null} when any position's entry
     * date sits before the CPI history starts: a partial base would be compared against the full
     * portfolio's current value and yield a misleading real return, so the whole metric is suppressed.
     */
    private BigDecimal sumRealCapitalBase(List<PortfolioPosition> positions,
                                          NavigableMap<LocalDate, BigDecimal> cpiByDate,
                                          BigDecimal cpiLatest) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            if (pos.getEntryDate() == null) continue;
            BigDecimal cpiAtEntry = cpiOnOrBefore(cpiByDate, pos.getEntryDate().toLocalDate());
            if (cpiAtEntry == null || cpiAtEntry.signum() <= 0) return null;
            BigDecimal entryValue = pos.entryValue();
            if (entryValue == null) continue;
            BigDecimal deflator = cpiLatest.divide(cpiAtEntry, RATIO_SCALE, RoundingMode.HALF_UP);
            sum = sum.add(entryValue.multiply(deflator));
        }
        return sum.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    private BigDecimal cpiOnOrBefore(NavigableMap<LocalDate, BigDecimal> cpiByDate, LocalDate date) {
        var entry = cpiByDate.floorEntry(date);
        return entry != null ? entry.getValue() : null;
    }

    private BigDecimal cpiGrowthPercent(NavigableMap<LocalDate, BigDecimal> cpiByDate,
                                        LocalDate earliest, BigDecimal cpiLatest) {
        BigDecimal cpiStart = cpiOnOrBefore(cpiByDate, earliest);
        if (cpiStart == null || cpiStart.signum() <= 0) return null;
        return cpiLatest.subtract(cpiStart).multiply(HUNDRED)
                .divide(cpiStart, MoneyScale.PRICE, RoundingMode.HALF_UP);
    }
}
