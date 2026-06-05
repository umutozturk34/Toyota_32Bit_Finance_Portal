package com.finance.portfolio.service.pricing;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.portfolio.dto.response.CurrencyFramePct;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Restates portfolio value and PnL in alternative currency frames (USD, EUR) alongside TRY, so the
 * UI can show "how did this do in dollars". Today's TRY value is divided by today's FX rate, while
 * each lot's entry cost is divided by its own entry-date rate, isolating real performance from FX
 * drift. Frames yield empty when the required rates are unavailable.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MultiCurrencyPnlCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** Below one cent a foreign daily P&L is FX-rounding residue on a non-moving position, not a real move → snap to 0. */
    private static final BigDecimal DAILY_FX_DUST = new BigDecimal("0.01");
    private static final List<String> TARGETS = List.of("USD", "EUR");
    private static final List<String> ALL_FRAMES = List.of("TRY", "USD", "EUR");
    private static final int LOOKBACK_DAYS = 7;

    private final HistoricalPricingPort historicalPricingPort;

    /** Returns the value/PnL frame per currency (always TRY, plus USD/EUR when rates allow), keyed by currency code. */
    public Map<String, CurrencyFramePct> compute(
            List<PortfolioPosition> positions,
            BigDecimal totalValueTry,
            BigDecimal dailyPnlTry,
            BigDecimal pnlPercentTry,
            BigDecimal dailyPnlPercentTry) {
        List<RealReturnCalculator.EntryFootprint> footprints = positions.stream()
                .filter(p -> p.getEntryPrice() != null && p.getQuantity() != null)
                .map(p -> {
                    BigDecimal entryVal = p.getEntryPrice().multiply(p.getQuantity());
                    // Closed lots carry exit date + proceeds so the frame locks them at exit-date FX (matching
                    // the per-point chart), instead of re-pricing frozen TRY proceeds at today's rate.
                    LocalDate exit = p.getExitDate() != null ? p.getExitDate().toLocalDate() : null;
                    BigDecimal exitVal = (exit != null && p.realizedPnl() != null)
                            ? p.realizedPnl().add(entryVal) : null;
                    return new RealReturnCalculator.EntryFootprint(
                            p.getEntryDate() != null ? p.getEntryDate().toLocalDate() : LocalDate.now(),
                            entryVal, exit, exitVal);
                })
                .toList();
        return computeFromFootprints(footprints, totalValueTry, dailyPnlTry, pnlPercentTry, dailyPnlPercentTry);
    }

    /**
     * Frame computation from a flat (entryDate, entryValueTry) list — same shape RealReturnCalculator
     * consumes, so portfolio-wide views can fold spot + derivative entries into a single basis. Without
     * this, the TRY frame's totalEntryTry was spot-only while {@code totalValueTry} already included
     * derivative lifecycle value, producing a totalPnlTry that disagreed with the headline card and
     * an inflated USD/EUR frame % whenever the portfolio held VIOP.
     */
    public Map<String, CurrencyFramePct> computeFromFootprints(
            List<RealReturnCalculator.EntryFootprint> footprints,
            BigDecimal totalValueTry,
            BigDecimal dailyPnlTry,
            BigDecimal pnlPercentTry,
            BigDecimal dailyPnlPercentTry) {
        Map<String, CurrencyFramePct> frames = new LinkedHashMap<>();
        if (totalValueTry == null) {
            BigDecimal totalEntryTry = sumEntryFootprints(footprints);
            frames.put("TRY", new CurrencyFramePct(pnlPercentTry, dailyPnlPercentTry,
                    null, totalEntryTry, null, dailyPnlTry));
            for (String target : TARGETS) frames.put(target, CurrencyFramePct.empty());
            return frames;
        }

        LocalDate today = LocalDate.now();
        LocalDate oldestEntry = footprints.stream()
                .filter(f -> f.entryDate() != null)
                .map(RealReturnCalculator.EntryFootprint::entryDate)
                .min(LocalDate::compareTo)
                .orElse(today.minusYears(1));

        // TRY runs the SAME frame path as USD/EUR with FX ≡ 1, so a VIOP SHORT's direction correction +
        // the value = cost + PnL identity apply uniformly — one source of truth, no per-currency special case
        // that drifts from the rest (the old TRY branch used value − cost and read a short's PnL backwards).
        for (String target : ALL_FRAMES) {
            frames.put(target, computeFrame(target, footprints, totalValueTry, dailyPnlTry,
                    today, oldestEntry));
        }
        return frames;
    }

    private BigDecimal sumEntryFootprints(List<RealReturnCalculator.EntryFootprint> footprints) {
        BigDecimal total = BigDecimal.ZERO;
        for (RealReturnCalculator.EntryFootprint fp : footprints) {
            if (fp.entryValueTry() == null) continue;
            total = total.add(fp.entryValueTry());
        }
        return total;
    }

    private CurrencyFramePct computeFrame(String target,
                                          List<RealReturnCalculator.EntryFootprint> footprints,
                                          BigDecimal totalValueTry, BigDecimal dailyPnlTry,
                                          LocalDate today, LocalDate oldestEntry) {
        TreeMap<LocalDate, BigDecimal> fxSeries = "TRY".equals(target)
                ? constantOneSeries(oldestEntry)
                : loadSeries(target, oldestEntry, today);
        // The live headline reuses the same primitive as every chart point, so closed lots lock at their
        // exit-date FX here too — the summary card and the Total/Open/Closed chart can no longer disagree.
        PointFrame frame = pointFrame(totalValueTry, footprints, today, fxSeries);
        if (frame.valueInTarget() == null || frame.costBasis() == null) {
            return CurrencyFramePct.empty();
        }
        BigDecimal entryInTarget = frame.costBasis();
        // + derivativeAdjustment flips a VIOP SHORT's sign so the foreign PnL is direction-aware (0 for spot/LONG).
        BigDecimal pnlInTarget = frame.valueInTarget().subtract(entryInTarget)
                .add(frame.derivativeAdjustment())
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        // Displayed VALUE = cost + PnL (EQUITY), not the raw notional. For spot/LONG this equals the notional
        // (PnL == value − cost), so nothing changes; for a profiting VIOP SHORT the notional FALLS below cost
        // while equity RISES (cost + profit) — the value the user expects ("kâr fiyata eklenmeli"). Because
        // value − cost ≡ PnL by construction now, the card / donut / chart can never disagree on the sign.
        BigDecimal todayInTarget = entryInTarget.add(pnlInTarget);
        BigDecimal pnlPct = entryInTarget.signum() > 0
                ? pnlInTarget.multiply(HUNDRED)
                        .divide(entryInTarget, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;

        BigDecimal dailyPct = null;
        BigDecimal dailyPnlInTarget = null;
        BigDecimal fxToday = closestPrior(fxSeries, today);
        if (dailyPnlTry != null && fxToday != null && fxToday.signum() > 0) {
            // Foreign daily = the TRY daily converted at TODAY's single FX rate. "Günlük K/Z" = "how much
            // moved today"; converting it at one rate keeps the frames consistent (USD daily = TRY daily ÷
            // FX), so a TRY daily of 0 reads 0 in USD/EUR too, and a real move shows as itself ("ya 50 ya 0").
            // Valuing today's and yesterday's VALUE at their OWN dates instead leaked the USD/TRY day-move onto
            // a non-moving position — a closed or USD-native VIOP showed ₺0 in TRY yet a phantom −$2.88 in USD.
            dailyPnlInTarget = dailyPnlTry.divide(fxToday, MoneyScale.PRICE, RoundingMode.HALF_UP);
            // Snap sub-cent residue to exactly 0: a non-moving (closed/hedged) position whose TRY daily is 0 must
            // read 0 in USD/EUR too, not a ±0.0001 rounding ghost that the chart's auto-zoom magnifies into waves.
            if (dailyPnlInTarget.abs().compareTo(DAILY_FX_DUST) < 0) dailyPnlInTarget = BigDecimal.ZERO;
            BigDecimal yesterdayValue = todayInTarget.subtract(dailyPnlInTarget);
            if (yesterdayValue.signum() > 0) {
                dailyPct = dailyPnlInTarget.multiply(HUNDRED)
                        .divide(yesterdayValue, MoneyScale.PRICE, RoundingMode.HALF_UP);
            }
        }

        return new CurrencyFramePct(pnlPct, dailyPct, todayInTarget, entryInTarget, pnlInTarget, dailyPnlInTarget);
    }

    /** FX ≡ 1 series so the TRY frame runs the identical {@link #pointFrame} path as USD/EUR (every cost/value/
     *  exit leg divides by 1). A single floor entry well before the oldest lot makes closestPrior always return 1. */
    private TreeMap<LocalDate, BigDecimal> constantOneSeries(LocalDate oldestEntry) {
        TreeMap<LocalDate, BigDecimal> one = new TreeMap<>();
        one.put(oldestEntry.minusYears(50), BigDecimal.ONE);
        return one;
    }

    private TreeMap<LocalDate, BigDecimal> loadSeries(String target, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> raw = historicalPricingPort.getPriceSeries(
                MarketType.FOREX, target, from.minusDays(LOOKBACK_DAYS), to.plusDays(1));
        if (raw == null || raw.isEmpty()) return new TreeMap<>();
        return new TreeMap<>(raw);
    }

    private BigDecimal closestPrior(TreeMap<LocalDate, BigDecimal> series, LocalDate date) {
        if (series.isEmpty()) return null;
        Map.Entry<LocalDate, BigDecimal> entry = series.floorEntry(date);
        // Date precedes all loaded FX history (no prior day) → fall back to the EARLIEST available rate, never
        // null. Returning null here dropped the value leg for pre-history points, so a foreign-currency chart
        // read 0 (then ramped) on early days while TRY — sourced from the always-present aggregate — stayed flat.
        // Mirrors the frontend rateAt earliest-point fallback; never today's spot.
        if (entry == null) entry = series.firstEntry();
        if (entry != null && entry.getValue() != null && entry.getValue().signum() > 0) {
            return entry.getValue();
        }
        return null;
    }

    /**
     * THE single source of truth for expressing a portfolio/asset value+PnL in a display currency at ONE
     * point in time: returns value@date (totalValueTry at the point-date FX) and the entry-date-FX cost
     * basis (Σ over the lots open at that date of entryValueTry / FX(entryDate)). PnL = value − cost is then
     * correct even when a lot's entry-date FX differs wildly from the point date — a 1995 USD lot keeps its
     * 0.044 entry rate instead of being re-valued at today's ~46 (which would fabricate ~+104000% profit).
     * The same primitive {@link #computeFrame} uses for the live headline; here it is callable per chart
     * point so the whole time series is range/filter-robust. Pass a pre-loaded {@code fxSeries} (see
     * {@link #loadFxSeries}) so a long series does not re-fetch FX per point.
     */
    public PointFrame pointFrame(BigDecimal valueTry, List<RealReturnCalculator.EntryFootprint> footprints,
                                 LocalDate date, TreeMap<LocalDate, BigDecimal> fxSeries) {
        if (fxSeries == null) return new PointFrame(null, null, null, BigDecimal.ZERO);
        BigDecimal fxAt = closestPrior(fxSeries, date);
        BigDecimal cost = BigDecimal.ZERO;       // all lots' entry cost at entry-date FX
        BigDecimal closedExitTry = BigDecimal.ZERO; // closed-by-date proceeds in TRY (locked in valueTry)
        BigDecimal closedValue = BigDecimal.ZERO;   // those proceeds re-priced at their OWN exit-date FX
        BigDecimal realized = BigDecimal.ZERO;      // closed lots' realized PnL, locked at exit-date FX
        BigDecimal derivativeAdjustment = BigDecimal.ZERO; // VIOP-short direction correction to (value − cost)
        boolean any = false;
        for (RealReturnCalculator.EntryFootprint fp : footprints) {
            if (fp == null || fp.entryValueTry() == null) continue;
            LocalDate entryDate = fp.entryDate() != null ? fp.entryDate() : date;
            if (entryDate.isAfter(date)) continue; // lot not yet entered at this point
            BigDecimal fxEntry = closestPrior(fxSeries, entryDate);
            if (fxEntry == null || fxEntry.signum() <= 0) continue;
            BigDecimal lotCost = fp.entryValueTry().divide(fxEntry, MoneyScale.PRICE, RoundingMode.HALF_UP);
            cost = cost.add(lotCost);
            any = true;
            // OPEN VIOP direction correction: the frame's PnL = value − cost is the notional change — correct
            // for a LONG but BACKWARDS for a SHORT (notional falls as it profits). (directionSign − 1) × (value −
            // cost) flips a SHORT (−2×) and leaves a LONG (0) untouched; per-date FX keeps $/€ kur-aware. Gated
            // on exitDate == null so a closed lot (handled below, at exit FX) does not also fire here.
            if (fp.exitDate() == null && fp.currentValueTry() != null && fp.directionSign() != 1
                    && fxAt != null && fxAt.signum() > 0) {
                BigDecimal lotValue = fp.currentValueTry().divide(fxAt, MoneyScale.PRICE, RoundingMode.HALF_UP);
                derivativeAdjustment = derivativeAdjustment.add(
                        BigDecimal.valueOf(fp.directionSign() - 1L).multiply(lotValue.subtract(lotCost)));
            }
            // A lot closed on/before this point sits in valueTry as frozen TRY proceeds; converting that at the
            // point-date rate fabricates FX drift after the sale. Pull it out and re-price at its exit-date FX.
            if (fp.exitDate() != null && !fp.exitDate().isAfter(date) && fp.exitValueTry() != null) {
                BigDecimal fxExit = closestPrior(fxSeries, fp.exitDate());
                if (fxExit != null && fxExit.signum() > 0) {
                    BigDecimal lotExit = fp.exitValueTry().divide(fxExit, MoneyScale.PRICE, RoundingMode.HALF_UP);
                    closedExitTry = closedExitTry.add(fp.exitValueTry());
                    closedValue = closedValue.add(lotExit);
                    BigDecimal lotRealized = lotExit.subtract(lotCost);
                    // CLOSED VIOP direction correction. The frame's raw PnL for this lot is (exitValueTry/fxExit −
                    // cost), where exitValueTry is whatever the caller folded into valueTry (proceeds for the
                    // summary card, closeNotional for the snapshot/perf series). The TRUE direction-aware PnL is
                    // sign × (closeNotional@exitFX − cost). Adding the difference makes both bases converge: it
                    // cancels the proceeds-vs-notional gap AND flips the SHORT's sign in one term.
                    if (fp.directionSign() != 1 && fp.currentValueTry() != null) {
                        BigDecimal closeNotionalAtExit = fp.currentValueTry().divide(fxExit, MoneyScale.PRICE, RoundingMode.HALF_UP);
                        BigDecimal directional = BigDecimal.valueOf(fp.directionSign())
                                .multiply(closeNotionalAtExit.subtract(lotCost));
                        BigDecimal corr = directional.subtract(lotRealized);
                        derivativeAdjustment = derivativeAdjustment.add(corr);
                        lotRealized = directional;
                    }
                    realized = realized.add(lotRealized);
                }
            }
        }
        BigDecimal value = null;
        if (valueTry != null && fxAt != null && fxAt.signum() > 0) {
            BigDecimal openValueTry = valueTry.subtract(closedExitTry);
            // Clamp the open leg at 0: for a FULLY-closed position valueTry can dip below the closed proceeds, making
            // openValueTry negative — and dividing that negative residual by the POINT-date FX re-marks it daily, so a
            // closed position's USD/EUR value drifted ("kept moving" post-close). The closed portion is already locked at
            // its exit-date FX in closedValue, so the open leg must contribute 0 once nothing is open.
            if (openValueTry.signum() < 0) openValueTry = BigDecimal.ZERO;
            value = openValueTry.divide(fxAt, MoneyScale.PRICE, RoundingMode.HALF_UP).add(closedValue);
        }
        return new PointFrame(value, any ? cost : null, any ? realized : null, derivativeAdjustment);
    }

    /** Pre-loaded FX series for {@code target} over the window, reused across many {@link #pointFrame} calls. */
    public TreeMap<LocalDate, BigDecimal> loadFxSeries(String target, LocalDate from, LocalDate to) {
        return loadSeries(target, from, to);
    }

    /**
     * One target-currency frame for a single time-series point: {@code valueInTarget} = open value at the
     * point-date FX plus closed proceeds locked at their exit-date FX; {@code costBasis} = every lot's entry
     * cost at its entry-date FX; {@code realized} = closed lots' PnL locked at exit-date FX. Total PnL =
     * value − cost + derivativeAdjustment; open PnL = total − realized. {@code derivativeAdjustment} flips a
     * VIOP SHORT's sign (0 for spot/LONG) so the foreign-currency PnL is direction-aware, per-date.
     */
    public record PointFrame(BigDecimal valueInTarget, BigDecimal costBasis, BigDecimal realized,
                             BigDecimal derivativeAdjustment) {}
}
