package com.finance.portfolio.service.pricing;

import com.finance.market.core.service.HistoricalPricingPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiCurrencyPnlCalculatorTest {

    @Mock private HistoricalPricingPort historicalPricingPort;

    /**
     * The central per-point frame must convert each lot's cost at its OWN entry-date FX, not the point
     * date — so a 1995 USD lot (USD/TRY 0.044) shows ~0 USD PnL today, not its whole value as "profit".
     */
    @Test
    void pointFrame_convertsCostAtEntryDateFx_notPointDate() {
        MultiCurrencyPnlCalculator calc = new MultiCurrencyPnlCalculator(historicalPricingPort);
        TreeMap<LocalDate, BigDecimal> fx = new TreeMap<>();
        fx.put(LocalDate.of(1995, 5, 18), new BigDecimal("0.044"));   // USD/TRY at entry
        fx.put(LocalDate.of(2026, 6, 4), new BigDecimal("46"));       // USD/TRY today
        List<RealReturnCalculator.EntryFootprint> footprints = List.of(
                new RealReturnCalculator.EntryFootprint(LocalDate.of(1995, 5, 18), new BigDecimal("5.28"), null));

        MultiCurrencyPnlCalculator.PointFrame frame = calc.pointFrame(
                new BigDecimal("5520"), footprints, LocalDate.of(2026, 6, 4), fx);

        // value = 5520/46 = 120; cost = 5.28/0.044 = 120 (entry-date FX, NOT 46) → PnL = 0, not 5514/46.
        assertThat(frame.valueInTarget()).isEqualByComparingTo("120");
        assertThat(frame.costBasis()).isEqualByComparingTo("120");
    }

    @Test
    void pointFrame_usesEarliestFx_whenDatePrecedesFxHistory_neverNull() {
        MultiCurrencyPnlCalculator calc = new MultiCurrencyPnlCalculator(historicalPricingPort);
        TreeMap<LocalDate, BigDecimal> fx = new TreeMap<>();
        fx.put(LocalDate.of(2026, 6, 1), new BigDecimal("40"));   // FX history starts AFTER the lot's early days
        List<RealReturnCalculator.EntryFootprint> fps = List.of(
                new RealReturnCalculator.EntryFootprint(LocalDate.of(2026, 5, 25), new BigDecimal("4000"), null));

        // Point + entry both precede the earliest FX point → fall back to the earliest rate (40), NEVER null:
        // returning null dropped the value leg so a USD/EUR chart read 0 on early days while TRY stayed flat.
        MultiCurrencyPnlCalculator.PointFrame frame = calc.pointFrame(
                new BigDecimal("4000"), fps, LocalDate.of(2026, 5, 26), fx);

        assertThat(frame.valueInTarget()).isEqualByComparingTo("100");   // 4000 / 40
        assertThat(frame.costBasis()).isEqualByComparingTo("100");       // 4000 / 40 (not null → no $0 ramp)
    }

    /**
     * A lot closed before the point sits in valueTry as frozen TRY proceeds; it must be re-priced at its
     * exit-date FX, not the point date — otherwise its USD value drifts after the sale and fabricates PnL.
     */
    @Test
    void pointFrame_locksClosedLotAtExitFx_noPostSaleDrift() {
        MultiCurrencyPnlCalculator calc = new MultiCurrencyPnlCalculator(historicalPricingPort);
        TreeMap<LocalDate, BigDecimal> fx = new TreeMap<>();
        fx.put(LocalDate.of(2002, 5, 9), new BigDecimal("1.3745"));  // USD/TRY at entry
        fx.put(LocalDate.of(2026, 3, 11), new BigDecimal("44"));     // USD/TRY at exit
        fx.put(LocalDate.of(2026, 6, 4), new BigDecimal("46"));      // USD/TRY today (rose since exit)
        // Closed 0.5 USD lot: entry cost 0.5*1.3745 = 0.68725 TRY; exit proceeds 0.5*44 = 22 TRY.
        List<RealReturnCalculator.EntryFootprint> fps = List.of(
                new RealReturnCalculator.EntryFootprint(LocalDate.of(2002, 5, 9), new BigDecimal("0.68725"),
                        LocalDate.of(2026, 3, 11), new BigDecimal("22")));

        // valueTry holds the frozen 22 TRY proceeds; today's rate is 46 but the lot exited at 44.
        MultiCurrencyPnlCalculator.PointFrame frame = calc.pointFrame(
                new BigDecimal("22"), fps, LocalDate.of(2026, 6, 4), fx);

        // value = 22/44 = 0.5 (exit FX, NOT 22/46); cost = 0.68725/1.3745 = 0.5; realized = 0 → total PnL = 0.
        assertThat(frame.valueInTarget()).isEqualByComparingTo("0.5");
        assertThat(frame.costBasis()).isEqualByComparingTo("0.5");
        assertThat(frame.realized()).isEqualByComparingTo("0");
    }

    /**
     * The foreign daily K/Z converts the TRY daily at TODAY's single FX rate (USD daily = TRY daily ÷ FX),
     * so the frames stay consistent and no USD/TRY day-move leaks onto a non-moving position. A per-date
     * value−value difference instead showed ₺0 in TRY yet a phantom ≠0 in USD for a closed / USD-native VIOP.
     * TRY daily 460 at today's USD/TRY 46 → 460/46 = 10.
     */
    @Test
    void computeFrame_dailyIsTryDailyAtTodayFx_singleRate() {
        java.util.Map<LocalDate, BigDecimal> usdFx = new java.util.HashMap<>();
        usdFx.put(LocalDate.of(2024, 6, 1), new BigDecimal("40"));   // entry-date FX
        usdFx.put(LocalDate.of(2024, 6, 4), new BigDecimal("46"));   // latest data day → today's FX
        when(historicalPricingPort.getPriceSeries(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("USD"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(usdFx);
        MultiCurrencyPnlCalculator calc = new MultiCurrencyPnlCalculator(historicalPricingPort);
        List<RealReturnCalculator.EntryFootprint> fps = List.of(
                new RealReturnCalculator.EntryFootprint(LocalDate.of(2024, 6, 1), new BigDecimal("2000"), null));

        var frames = calc.computeFromFootprints(fps, new BigDecimal("4600"), new BigDecimal("460"),
                new BigDecimal("10"), new BigDecimal("10"));

        assertThat(frames.get("USD").dailyPnl()).isEqualByComparingTo("10");
    }

    /**
     * A CLOSED VIOP SHORT that profited (notional fell) must read as a PROFIT in USD too. Pricing its
     * proceeds (= entry notional + realized) at the exit FX and subtracting cost at the entry FX leaks the FX
     * drift on the WHOLE notional — when TRY weakened it drags the small realized negative, showing the profit
     * as a loss. The direction correction restores sign × (closeNotional@exitFX − cost@entryFX).
     */
    @Test
    void pointFrame_closedShortViop_directionAwareProfitInTarget() {
        MultiCurrencyPnlCalculator calc = new MultiCurrencyPnlCalculator(historicalPricingPort);
        TreeMap<LocalDate, BigDecimal> fx = new TreeMap<>();
        fx.put(LocalDate.of(2026, 1, 1), new BigDecimal("10"));   // USD/TRY at entry
        fx.put(LocalDate.of(2026, 6, 5), new BigDecimal("11"));   // USD/TRY at close (TRY weakened 10%)
        // SHORT closed at a profit: entry notional 1000 TRY, realized +20 (price fell) → proceeds 1020 TRY,
        // close notional = 1000 − 20 = 980 TRY (the value leg). Caller folds proceeds into valueTry here.
        // cost@entryFX = 1000/10 = 100.
        List<RealReturnCalculator.EntryFootprint> fps = List.of(
                RealReturnCalculator.EntryFootprint.viopClosed(LocalDate.of(2026, 1, 1), new BigDecimal("1000"),
                        LocalDate.of(2026, 6, 5), new BigDecimal("1020"), new BigDecimal("980"), -1));

        MultiCurrencyPnlCalculator.PointFrame frame = calc.pointFrame(
                new BigDecimal("1020"), fps, LocalDate.of(2026, 6, 5), fx);

        // Without the correction USD PnL = proceeds 1020/11 − 100 = −7.27 (a LOSS). Correction =
        // (−1−1)×(1000/11 − 100) = +18.18, so realized = +10.91 (PROFIT) and the donut/card flip green.
        assertThat(frame.derivativeAdjustment()).isCloseTo(new BigDecimal("18.1818"), within(new BigDecimal("0.001")));
        assertThat(frame.realized()).isCloseTo(new BigDecimal("10.9091"), within(new BigDecimal("0.001")));
        assertThat(frame.costBasis()).isEqualByComparingTo("100");
    }

    /**
     * Basis independence: the snapshot/perf series folds the CLOSE NOTIONAL (not proceeds) into valueTry for a
     * closed VIOP, so the footprint's value leg is closeNotional too. The frame must still derive the SAME
     * direction-aware realized (+10.91) as the proceeds-basis card — different value/derivativeAdjustment, same PnL.
     */
    @Test
    void pointFrame_closedShortViop_sameRealized_whenValueLegIsCloseNotional() {
        MultiCurrencyPnlCalculator calc = new MultiCurrencyPnlCalculator(historicalPricingPort);
        TreeMap<LocalDate, BigDecimal> fx = new TreeMap<>();
        fx.put(LocalDate.of(2026, 1, 1), new BigDecimal("10"));
        fx.put(LocalDate.of(2026, 6, 5), new BigDecimal("11"));
        // Same SHORT, but the value leg = close notional 980 (perf basis), and valueTry carries 980 too.
        List<RealReturnCalculator.EntryFootprint> fps = List.of(
                RealReturnCalculator.EntryFootprint.viopClosed(LocalDate.of(2026, 1, 1), new BigDecimal("1000"),
                        LocalDate.of(2026, 6, 5), new BigDecimal("980"), new BigDecimal("980"), -1));

        MultiCurrencyPnlCalculator.PointFrame frame = calc.pointFrame(
                new BigDecimal("980"), fps, LocalDate.of(2026, 6, 5), fx);

        // value − cost = 980/11 − 100 = −10.91 (notional change), derivativeAdjustment = +21.82 → realized +10.91.
        assertThat(frame.realized()).isCloseTo(new BigDecimal("10.9091"), within(new BigDecimal("0.001")));
        assertThat(frame.valueInTarget().subtract(frame.costBasis()).add(frame.derivativeAdjustment()))
                .isCloseTo(new BigDecimal("10.9091"), within(new BigDecimal("0.001")));
    }

    @Test
    void pointFrame_nullFxSeries_returnsNulls() {
        MultiCurrencyPnlCalculator calc = new MultiCurrencyPnlCalculator(historicalPricingPort);
        MultiCurrencyPnlCalculator.PointFrame frame = calc.pointFrame(
                new BigDecimal("100"), List.of(), LocalDate.of(2026, 6, 4), null);

        assertThat(frame.valueInTarget()).isNull();
        assertThat(frame.costBasis()).isNull();
    }

    /**
     * A lot entered TODAY (the latest FX day) was not held on the prior day, so its daily K/Z in a foreign
     * frame must be 0 — not the FX day-move repriced onto its whole value. Before the fix the prior-day leg
     * repriced the same TRY value at yesterday's FX (45) vs today's (46), fabricating a phantom daily even
     * though the TRY daily was 0. This is the DNISI.IS same-day −$0.0002 (−0.05%) case: TRY shows 0, USD must too.
     */
    @Test
    void computeFrame_sameDayLot_foreignDailyIsZero_noFxDateRepricing() {
        java.util.Map<LocalDate, BigDecimal> usdFx = new java.util.HashMap<>();
        usdFx.put(LocalDate.of(2024, 6, 3), new BigDecimal("45"));   // prior FX day
        usdFx.put(LocalDate.of(2024, 6, 4), new BigDecimal("46"));   // latest FX day == entry day
        when(historicalPricingPort.getPriceSeries(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("USD"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(usdFx);
        MultiCurrencyPnlCalculator calc = new MultiCurrencyPnlCalculator(historicalPricingPort);
        // Bought on the latest FX day with no move: value today == entry == 4600 TRY, TRY daily 0.
        List<RealReturnCalculator.EntryFootprint> fps = List.of(
                new RealReturnCalculator.EntryFootprint(LocalDate.of(2024, 6, 4), new BigDecimal("4600"), null));

        var frames = calc.computeFromFootprints(fps, new BigDecimal("4600"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);

        // 4600/46 today vs 4600/46 entry (both at the entry-day FX) → 0; NOT 4600×(1/46 − 1/45) phantom.
        assertThat(frames.get("USD").dailyPnl()).isEqualByComparingTo("0");
    }
}
