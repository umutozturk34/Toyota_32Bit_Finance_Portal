package com.finance.portfolio.service.pricing;

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
 *
 * <p>This is the same geometric (Fisher) real-return relationship as
 * {@code com.finance.shared.util.ReturnMath.realExcessPct} (the shared primitive the scenario and
 * inflation-beater use), applied value-side: a single lot reduces to {@code (1+nominal)/(1+cpiGrowth)-1},
 * but the portfolio aggregate deflates EACH lot by ITS OWN entry-date CPI (money-weighted) and so cannot
 * collapse to a single (return, CPI) pair — hence the value-based capital-base form is kept here.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class RealReturnCalculator {

    // TP.GENENDEKS.T1 is the actual TÜFE (CPI) series despite the misleading TCMB EVDS code
    // naming — TP.TUFE1YI.T1 is in fact Yİ-ÜFE (PPI). Real-return deflation needs CPI; kept in
    // sync with ScenarioService.CPI_CODE, InflationBeaterService.DEFAULT_BENCHMARK, and macro.yaml.
    private static final String CPI_CODE = "TP.GENENDEKS.T1";
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

    /**
     * One position's contribution to the real-return capital base: its entry date, entry value in TRY,
     * and (when closed) the lot's exit date. Lets the calculator deflate the lot by CPI without depending
     * on the concrete position type (spot {@code PortfolioPosition} vs derivative {@code DerivativePosition}).
     * Both must contribute for real-return % to be comparable to nominal % — otherwise the denominator
     * excludes VIOP entries while the numerator (totalValueTry) includes their lifecycle value, inflating
     * the real % so it exceeds the nominal %, which is mathematically impossible when CPI growth ≥ 0.
     *
     * <p>{@code exitDate} is {@code null} for an open lot (deflate entry→today) and set for a closed lot
     * (deflate entry→exit), mirroring {@link #computePerPosition}'s closed branch so the portfolio-level
     * and per-position real-return paths agree for any portfolio holding closed lots.
     */
    public record EntryFootprint(LocalDate entryDate, BigDecimal entryValueTry, LocalDate exitDate,
                                 BigDecimal exitValueTry, int directionSign, BigDecimal currentValueTry) {
        /** Back-compat: spot/closed lots are LONG-like (value − cost == PnL); no direction override. */
        public EntryFootprint(LocalDate entryDate, BigDecimal entryValueTry, LocalDate exitDate, BigDecimal exitValueTry) {
            this(entryDate, entryValueTry, exitDate, exitValueTry, 1, null);
        }
        /** Back-compat: open lots and CPI-only callers carry no exit proceeds. */
        public EntryFootprint(LocalDate entryDate, BigDecimal entryValueTry, LocalDate exitDate) {
            this(entryDate, entryValueTry, exitDate, null);
        }
        /**
         * Open VIOP lot whose PnL is DIRECTION-AWARE: the currency frame computes value − cost (the notional
         * change), correct for a LONG but BACKWARDS for a SHORT (its notional falls as it profits).
         * {@code directionSign} (+1 LONG, −1 SHORT) and {@code currentValueTry} (today's notional in TRY)
         * let the frame add a per-date correction so the foreign-currency PnL carries the right sign.
         */
        public static EntryFootprint viopOpen(LocalDate entryDate, BigDecimal entryNotionalTry,
                                              int directionSign, BigDecimal currentNotionalTry) {
            return new EntryFootprint(entryDate, entryNotionalTry, null, null, directionSign, currentNotionalTry);
        }
        /**
         * Closed VIOP lot: like any closed lot it carries exit proceeds (= entry notional + realized) so the
         * TRY frame's value − cost equals the realized PnL, but it ALSO carries {@code directionSign} so the
         * currency frame can flip a SHORT's foreign-currency PnL. Converting proceeds at the exit-date FX and
         * subtracting cost at the entry-date FX is correct for a LONG, but for a SHORT it leaks the FX drift on
         * the full notional (which dwarfs the small realized), reading a profit as a loss; the frame applies a
         * direction correction keyed on {@code directionSign} + {@code exitDate}.
         *
         * <p>{@code closeNotionalTry} (stored in {@code currentValueTry}) is the direction-blind notional at
         * close (close price × size × lots), the frame's true value leg; {@code exitProceedsTry} is whatever
         * the CALLER folds into its {@code valueTry} for this lot (proceeds for the summary card, closeNotional
         * for the snapshot/perf series). The frame derives the exact direction-aware PnL — sign × (closeNotional@
         * exitFX − cost) — regardless of which basis the caller used, so card / donut / perf all converge. */
        public static EntryFootprint viopClosed(LocalDate entryDate, BigDecimal entryNotionalTry,
                                                LocalDate exitDate, BigDecimal exitProceedsTry,
                                                BigDecimal closeNotionalTry, int directionSign) {
            return new EntryFootprint(entryDate, entryNotionalTry, exitDate, exitProceedsTry, directionSign, closeNotionalTry);
        }
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
            // A closed lot's realized cash is frozen at exit, so deflate entry→exit (not entry→today);
            // using cpiLatest would keep inflating the lot's real loss every month after it closed.
            BigDecimal cpiEndpoint = cpiLatest;
            if (pos.isClosed() && pos.getExitDate() != null) {
                BigDecimal cpiAtExit = cpiOnOrBefore(cpiByDate, pos.getExitDate().toLocalDate());
                if (cpiAtExit != null && cpiAtExit.signum() > 0) cpiEndpoint = cpiAtExit;
            }
            BigDecimal deflator = cpiEndpoint.divide(cpiAtEntry, RATIO_SCALE, RoundingMode.HALF_UP);
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
        if (positions == null) return RealReturnSummary.EMPTY;
        List<EntryFootprint> footprints = positions.stream()
                .filter(p -> p.getEntryDate() != null && p.entryValue() != null)
                .map(p -> new EntryFootprint(p.getEntryDate().toLocalDate(), p.entryValue(),
                        p.isClosed() && p.getExitDate() != null ? p.getExitDate().toLocalDate() : null))
                .toList();
        return computeFromFootprints(footprints, totalValueTry);
    }

    /**
     * Portfolio real return from a mixed list of entry footprints (spot + derivative). Use this when
     * a portfolio holds both kinds — passing only spot positions to the legacy {@link #compute} would
     * deflate just the spot entry while {@code totalValueTry} includes derivative lifecycle value, so
     * the resulting real % overstates the truth (regression observed: real 67.5% > nominal 26.4%).
     */
    @Transactional(readOnly = true)
    public RealReturnSummary computeFromFootprints(List<EntryFootprint> footprints, BigDecimal totalValueTry) {
        if (footprints == null || footprints.isEmpty() || totalValueTry == null) {
            return RealReturnSummary.EMPTY;
        }
        LocalDate earliest = earliestFootprintDate(footprints);
        if (earliest == null) return RealReturnSummary.EMPTY;

        NavigableMap<LocalDate, BigDecimal> cpiByDate = loadCpi(earliest);
        if (cpiByDate.size() < 2) {
            log.warn("CPI history insufficient for real return earliest={} points={} (need >= 2) — suppressing real-return metric", earliest, cpiByDate.size());
            return RealReturnSummary.EMPTY;
        }

        BigDecimal cpiLatest = cpiByDate.lastEntry().getValue();
        BigDecimal realCapitalBase = sumRealCapitalBaseFromFootprints(footprints, cpiByDate, cpiLatest);
        if (realCapitalBase == null || realCapitalBase.signum() <= 0) return RealReturnSummary.EMPTY;

        BigDecimal realPnl = totalValueTry.subtract(realCapitalBase).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal realPct = realPnl.multiply(HUNDRED).divide(realCapitalBase, MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal cpiGrowthPct = cpiGrowthPercent(cpiByDate, earliest, cpiLatest);

        log.debug("Real return computed footprints={} earliest={} realPnl={} realPct={} cpiGrowthPct={}",
                footprints.size(), earliest, realPnl, realPct, cpiGrowthPct);
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

    private LocalDate earliestFootprintDate(List<EntryFootprint> footprints) {
        LocalDate earliest = null;
        for (EntryFootprint fp : footprints) {
            if (fp.entryDate() == null) continue;
            if (earliest == null || fp.entryDate().isBefore(earliest)) earliest = fp.entryDate();
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
            log.warn("CPI lookup failed for real return calc error={} — suppressing real-return metric", e.getMessage());
            return new TreeMap<>();
        }
    }

    /**
     * Sums each footprint's CPI-deflated entry value. Returns {@code null} when any entry date sits
     * before the CPI history starts: a partial base would be compared against the full portfolio's
     * current value and yield a misleading real return, so the whole metric is suppressed.
     */
    private BigDecimal sumRealCapitalBaseFromFootprints(List<EntryFootprint> footprints,
                                                        NavigableMap<LocalDate, BigDecimal> cpiByDate,
                                                        BigDecimal cpiLatest) {
        BigDecimal sum = BigDecimal.ZERO;
        for (EntryFootprint fp : footprints) {
            if (fp.entryDate() == null) continue;
            BigDecimal cpiAtEntry = cpiOnOrBefore(cpiByDate, fp.entryDate());
            if (cpiAtEntry == null || cpiAtEntry.signum() <= 0) return null;
            BigDecimal entryValue = fp.entryValueTry();
            if (entryValue == null) continue;
            // A closed lot's realized cash is frozen at exit, so deflate entry→exit (not entry→today),
            // matching computePerPosition's closed branch — otherwise the portfolio-level and
            // per-position real-return paths disagree once a portfolio holds closed lots.
            BigDecimal cpiEndpoint = cpiLatest;
            if (fp.exitDate() != null) {
                BigDecimal cpiAtExit = cpiOnOrBefore(cpiByDate, fp.exitDate());
                if (cpiAtExit != null && cpiAtExit.signum() > 0) cpiEndpoint = cpiAtExit;
            }
            BigDecimal deflator = cpiEndpoint.divide(cpiAtEntry, RATIO_SCALE, RoundingMode.HALF_UP);
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
