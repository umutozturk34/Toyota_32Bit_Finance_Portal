package com.finance.portfolio.service.performance;

import com.finance.portfolio.service.pricing.MultiCurrencyPnlCalculator;
import com.finance.portfolio.service.pricing.RealReturnCalculator;

import com.finance.portfolio.model.MoneyScale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The per-currency (USD/EUR) FX-frame core for the performance charts. Prices each lot at its own
 * entry-date FX for cost and the point-date FX for open value, while locking closed lots at their
 * exit-date FX so a realized series carries no post-sale FX drift. The single dependency is
 * {@link MultiCurrencyPnlCalculator}, whose {@code pointFrame}/{@code loadFxSeries} primitives this
 * orchestrates over a footprint set.
 */
@Component
@RequiredArgsConstructor
class PerCurrencyFrameCalculator {

    private static final List<String> FRAME_CCYS = List.of("USD", "EUR");

    private final MultiCurrencyPnlCalculator multiCurrencyPnlCalculator;

    /** Pre-load USD/EUR FX series over the footprints' span (once per request, reused across all points). */
    Map<String, TreeMap<LocalDate, BigDecimal>> fxSeriesByCcy(
            List<RealReturnCalculator.EntryFootprint> fps, LocalDate end) {
        LocalDate oldest = fps.stream().map(RealReturnCalculator.EntryFootprint::entryDate)
                .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(end.minusYears(1));
        Map<String, TreeMap<LocalDate, BigDecimal>> out = new LinkedHashMap<>();
        for (String ccy : FRAME_CCYS) out.put(ccy, multiCurrencyPnlCalculator.loadFxSeries(ccy, oldest, end));
        return out;
    }

    /** Per-currency entry-date-FX cost basis + point-date value for the lots open at {@code date}. */
    FrameMaps framesFor(LocalDate date, BigDecimal valueTry,
                        List<RealReturnCalculator.EntryFootprint> fps,
                        Map<String, TreeMap<LocalDate, BigDecimal>> fxByCcy) {
        List<RealReturnCalculator.EntryFootprint> open = fps.stream()
                .filter(f -> f.entryDate() != null && !f.entryDate().isAfter(date))
                .filter(f -> f.exitDate() == null || f.exitDate().isAfter(date))
                .toList();
        Map<String, BigDecimal> cost = new LinkedHashMap<>();
        Map<String, BigDecimal> value = new LinkedHashMap<>();
        for (String ccy : FRAME_CCYS) {
            MultiCurrencyPnlCalculator.PointFrame frame =
                    multiCurrencyPnlCalculator.pointFrame(valueTry, open, date, fxByCcy.get(ccy));
            if (frame.costBasis() != null) cost.put(ccy, frame.costBasis());
            if (frame.valueInTarget() != null) {
                // value = EQUITY = raw value + derivativeAdjustment, so a profiting open SHORT's per-currency open
                // value/PnL is direction-aware (per-date), not the FX-drifted notional. 0 for spot/LONG & plain
                // footprints (so other callers are untouched); only direction-carrying open VIOP flips.
                value.put(ccy, frame.valueInTarget().add(frame.derivativeAdjustment()));
            }
        }
        return new FrameMaps(cost, value);
    }

    /**
     * Aggregate per-currency frame over ALL lots (open + closed). Unlike {@link #framesFor} (open-only, used
     * per detail row), this passes every footprint so {@link MultiCurrencyPnlCalculator#pointFrame} can lock
     * each closed lot at its exit-date FX (no post-sale FX drift) and count its cost — value − cost is then
     * the true total PnL, and {@code realized} is the closed portion for the Total/Open/Closed split.
     *
     * <p>Closed lots are split out and valued in a SEPARATE locked frame (open footprints + the open value
     * portion go through {@code pointFrame}, closed ones through {@link #lockedClosedFrame}). A closed
     * derivative's realized cash is frozen at its close-date FX, so re-running it through the per-date frame —
     * where the caller's {@code valueTry} (the equity total) need not equal the footprint's exit proceeds —
     * left an open-value residual that the per-date {@code fxAt} re-marked each day, rounding to ±0.0001
     * phantom daily deltas (the chart's flat-series "dust"). Locking the closed portion makes a closed lot's
     * USD/EUR value identical on every post-close date (daily delta exactly 0); open lots keep per-date FX.
     */
    FrameMapsR framesForTotal(LocalDate date, BigDecimal valueTry,
                              List<RealReturnCalculator.EntryFootprint> fps,
                              Map<String, TreeMap<LocalDate, BigDecimal>> fxByCcy) {
        List<RealReturnCalculator.EntryFootprint> closedFps = new ArrayList<>();
        List<RealReturnCalculator.EntryFootprint> openFps = new ArrayList<>();
        BigDecimal closedExitTry = BigDecimal.ZERO;
        for (RealReturnCalculator.EntryFootprint fp : fps) {
            if (fp != null && fp.exitDate() != null && !fp.exitDate().isAfter(date)) {
                closedFps.add(fp);
                if (fp.exitValueTry() != null) closedExitTry = closedExitTry.add(fp.exitValueTry());
            } else if (fp != null) {
                openFps.add(fp);
            }
        }
        // Open value = the caller's total minus the closed lots' frozen proceeds, so the per-date frame only
        // re-marks genuinely-open value. For a fully-closed portfolio this is 0 → no per-date FX touches it.
        BigDecimal openValueTry = valueTry != null ? valueTry.subtract(closedExitTry) : null;

        Map<String, BigDecimal> cost = new LinkedHashMap<>();
        Map<String, BigDecimal> value = new LinkedHashMap<>();
        Map<String, BigDecimal> realized = new LinkedHashMap<>();
        Map<String, BigDecimal> pnl = new LinkedHashMap<>();
        for (String ccy : FRAME_CCYS) {
            TreeMap<LocalDate, BigDecimal> fx = fxByCcy.get(ccy);
            MultiCurrencyPnlCalculator.PointFrame frame =
                    multiCurrencyPnlCalculator.pointFrame(openValueTry, openFps, date, fx);
            LockedFrame closed = lockedClosedFrame(closedFps, date, fx);

            BigDecimal openCost = frame.costBasis();
            BigDecimal totalCost = sumNullable(openCost, closed.cost());
            if (totalCost != null) cost.put(ccy, totalCost);
            BigDecimal totalRealized = sumNullable(frame.realized(), closed.realized());
            if (totalRealized != null) realized.put(ccy, totalRealized);

            BigDecimal openPnl = (frame.valueInTarget() != null && openCost != null)
                    // + derivativeAdjustment makes an OPEN VIOP SHORT's foreign PnL direction-aware (0 for spot/LONG).
                    ? frame.valueInTarget().subtract(openCost).add(frame.derivativeAdjustment())
                    : null;
            BigDecimal totalPnl = sumNullable(openPnl, closed.pnl());
            if (totalPnl != null) {
                pnl.put(ccy, totalPnl);
                // Value line = EQUITY = cost + PnL: the closed portion's value is its locked (exit-FX) cost + PnL,
                // constant on every post-close date; the open portion stays per-date. value − cost ≡ PnL keeps the
                // value and K/Z charts in lockstep.
                if (totalCost != null) value.put(ccy, totalCost.add(totalPnl));
            } else if (frame.valueInTarget() != null) {
                value.put(ccy, frame.valueInTarget());
            }
        }
        return new FrameMapsR(cost, value, realized, pnl);
    }

    /**
     * Per-currency frame contribution of lots ALREADY CLOSED by {@code date}, locked at each lot's close-date
     * FX (never the point date's). cost = Σ entryValueTry / FX(entryDate); the realized/PnL mirror
     * {@link MultiCurrencyPnlCalculator#pointFrame}'s closed branch (proceeds@exitFX − cost, with the VIOP
     * SHORT direction flip). Independent of {@code date}'s FX → a closed lot's value (cost + PnL) is identical
     * on every post-close date, so the daily delta is exactly 0 (no FX-drift dust on a realized series).
     */
    private LockedFrame lockedClosedFrame(List<RealReturnCalculator.EntryFootprint> closedFps, LocalDate date,
                                          TreeMap<LocalDate, BigDecimal> fxSeries) {
        if (closedFps.isEmpty() || fxSeries == null) return LockedFrame.EMPTY;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        boolean any = false;
        for (RealReturnCalculator.EntryFootprint fp : closedFps) {
            if (fp.entryValueTry() == null || fp.exitValueTry() == null) continue;
            LocalDate entryDate = fp.entryDate() != null ? fp.entryDate() : date;
            BigDecimal fxEntry = floor(fxSeries, entryDate);
            BigDecimal fxExit = floor(fxSeries, fp.exitDate());
            if (fxEntry == null || fxEntry.signum() <= 0 || fxExit == null || fxExit.signum() <= 0) continue;
            BigDecimal lotCost = fp.entryValueTry().divide(fxEntry, MoneyScale.PRICE, RoundingMode.HALF_UP);
            BigDecimal lotExit = fp.exitValueTry().divide(fxExit, MoneyScale.PRICE, RoundingMode.HALF_UP);
            BigDecimal lotRealized = lotExit.subtract(lotCost);
            // CLOSED VIOP direction flip (mirrors pointFrame): the true PnL is sign × (closeNotional@exitFX − cost),
            // which both cancels the proceeds-vs-notional gap and flips a SHORT's sign. 0 for spot/LONG.
            if (fp.directionSign() != 1 && fp.currentValueTry() != null) {
                BigDecimal closeNotionalAtExit = fp.currentValueTry().divide(fxExit, MoneyScale.PRICE, RoundingMode.HALF_UP);
                lotRealized = BigDecimal.valueOf(fp.directionSign()).multiply(closeNotionalAtExit.subtract(lotCost));
            }
            cost = cost.add(lotCost);
            realized = realized.add(lotRealized);
            any = true;
        }
        return any ? new LockedFrame(cost, realized, realized) : LockedFrame.EMPTY;
    }

    private static BigDecimal floor(TreeMap<LocalDate, BigDecimal> series, LocalDate date) {
        Map.Entry<LocalDate, BigDecimal> e = series.floorEntry(date);
        return e != null ? e.getValue() : null;
    }

    /** Sum of two nullable addends, treating null as absent: {@code null + null = null}, else the present value(s). */
    private static BigDecimal sumNullable(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b);
    }
}
