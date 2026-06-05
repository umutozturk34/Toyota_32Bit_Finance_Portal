package com.finance.portfolio.service.performance;

import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the per-currency NOTIONAL reconstruction against double-counting a spot lot that was sold on the
 * point date. The close-day per-asset row (marketValueTry == exit proceeds) and the closed position's own
 * exitValue both used to land in the notional, doubling the USD/EUR frame on the sell day while TRY — protected
 * by the open-leg date-aware filter — stayed correct. These tests pin the date-aware exclusion that fixes it.
 */
class AggregatePerformanceBuilderPerCcyInputsTest {

    private static final LocalDateTime ENTRY = LocalDateTime.of(2026, 6, 1, 0, 0);
    private static final LocalDateTime EXIT_TODAY = LocalDateTime.of(2026, 6, 5, 0, 0);
    private static final java.time.LocalDate SELL_DAY = EXIT_TODAY.toLocalDate();
    private static final String BTC = "BTC";

    /** perCcyInputs uses none of the injected collaborators, so nulls are safe for this unit. */
    private final AggregatePerformanceBuilder builder =
            new AggregatePerformanceBuilder(null, null, null, null, null, null, null, null, null);

    private static PortfolioPosition closedTodayLot(BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal qty) {
        return PortfolioPosition.builder()
                .assetType(AssetType.CRYPTO).assetCode(BTC)
                .quantity(qty).entryPrice(entryPrice).entryDate(ENTRY)
                .exitPrice(exitPrice).exitDate(EXIT_TODAY)
                .build();
    }

    private static PortfolioPosition openLot(BigDecimal entryPrice, BigDecimal qty) {
        return PortfolioPosition.builder()
                .assetType(AssetType.CRYPTO).assetCode(BTC)
                .quantity(qty).entryPrice(entryPrice).entryDate(ENTRY)
                .build();
    }

    private static PortfolioAssetDailySnapshot closeDayRow(BigDecimal exitProceeds) {
        // SnapshotCalculationService.buildSymbolRow emits this on the sell day: marketValueTry == exit proceeds.
        return PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.CRYPTO).assetCode(BTC)
                .snapshotDate(SELL_DAY).createdAt(EXIT_TODAY)
                .marketValueTry(exitProceeds)
                .build();
    }

    @Test
    void should_countClosedSpotExitOnce_when_closeDayRowAndPositionExitBothPresent() {
        // Arrange: one BTC lot sold today. entry=40000, exit=53648.02, qty=1 → proceeds=53648.02. The close-day
        // row (marketValueTry=53648.02) AND the closed position (exitValue=53648.02) are both present for the day.
        BigDecimal proceeds = new BigDecimal("53648.02");
        PortfolioPosition lot = closedTodayLot(new BigDecimal("40000"), proceeds, BigDecimal.ONE);
        List<PortfolioAssetDailySnapshot> dayAssets = List.of(closeDayRow(proceeds));

        // Act
        PerCcyInputs pc = builder.perCcyInputs(List.of(lot), List.of(), dayAssets, SELL_DAY);

        // Assert: notional carries the proceeds ONCE (from the position footprint), not 2× — the close-day row is
        // excluded because BTC is closed as of the sell day. Pre-fix this was 2 × 53648.02 = 107296.04.
        assertThat(pc.notionalTry()).isEqualByComparingTo(proceeds);
    }

    @Test
    void should_keepOpenSpotRowInNotional_when_symbolStillOpenOnDate() {
        // Arrange: BTC still open on the date. Its per-asset row's market value must stay in the notional.
        BigDecimal marketValue = new BigDecimal("53648.02");
        PortfolioPosition lot = openLot(new BigDecimal("40000"), BigDecimal.ONE);
        PortfolioAssetDailySnapshot openRow = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.CRYPTO).assetCode(BTC)
                .snapshotDate(SELL_DAY).createdAt(EXIT_TODAY)
                .marketValueTry(marketValue)
                .build();

        // Act
        PerCcyInputs pc = builder.perCcyInputs(List.of(lot), List.of(), List.of(openRow), SELL_DAY);

        // Assert: open spot contributes its market value once; no closed proceeds to add.
        assertThat(pc.notionalTry()).isEqualByComparingTo(marketValue);
    }

    @Test
    void should_keepHistoricalValue_when_dateIsBeforeExit() {
        // Arrange: same lot sold on 06-05, but the point date is 06-03 (still open then). The row carries the
        // then-current market value and the date-aware filter must NOT exclude it (exit is after the point date).
        java.time.LocalDate historical = java.time.LocalDate.of(2026, 6, 3);
        BigDecimal histValue = new BigDecimal("50000.00");
        PortfolioPosition lot = closedTodayLot(new BigDecimal("40000"), new BigDecimal("53648.02"), BigDecimal.ONE);
        PortfolioAssetDailySnapshot histRow = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.CRYPTO).assetCode(BTC)
                .snapshotDate(historical).createdAt(historical.atStartOfDay())
                .marketValueTry(histValue)
                .build();

        // Act
        PerCcyInputs pc = builder.perCcyInputs(List.of(lot), List.of(), List.of(histRow), historical);

        // Assert: on a date before the exit the symbol is open, so its value stays and the exit proceeds are NOT
        // added (exit is after the point date) — the chart keeps its history rather than dropping to 0.
        assertThat(pc.notionalTry()).isEqualByComparingTo(histValue);
    }

    @Test
    void should_keepSnapshotOnlyRow_when_symbolHasNoMatchingPosition() {
        // Arrange: a row whose assetCode has no position at all (snapshots-only history) must be kept — the
        // close-symbol set is derived from positions, so an unmatched code is never treated as closed.
        BigDecimal marketValue = new BigDecimal("12345.67");
        PortfolioAssetDailySnapshot orphanRow = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.CRYPTO).assetCode("ETH")
                .snapshotDate(SELL_DAY).createdAt(EXIT_TODAY)
                .marketValueTry(marketValue)
                .build();

        // Act
        PerCcyInputs pc = builder.perCcyInputs(List.of(), List.of(), List.of(orphanRow), SELL_DAY);

        // Assert: orphan row's value is retained.
        assertThat(pc.notionalTry()).isEqualByComparingTo(marketValue);
    }
}
