package com.finance.portfolio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SnapshotTotalsTest {

    private static final Long PORTFOLIO_ID = 11L;
    private static final LocalDate SNAP_DATE = LocalDate.of(2026, 5, 1);
    private static final LocalDateTime BATCH_TS = LocalDateTime.of(2026, 5, 1, 18, 0);

    @Test
    void should_startAllTotalsAtZero_when_newlyConstructed() {
        SnapshotTotals totals = new SnapshotTotals();

        assertThat(totals.totalMarketValue).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.cumulativeRealized).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.closedExitValue).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.totalEntryValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_accumulateEntryValues_when_addEntryCalledMultipleTimes() {
        SnapshotTotals totals = new SnapshotTotals();

        totals.addEntry(new BigDecimal("100"));
        totals.addEntry(new BigDecimal("50.5"));

        assertThat(totals.totalEntryValue).isEqualByComparingTo("150.5");
    }

    @Test
    void should_accumulateMarketValues_when_addMarketCalledMultipleTimes() {
        SnapshotTotals totals = new SnapshotTotals();

        totals.addMarket(new BigDecimal("220"));
        totals.addMarket(new BigDecimal("80"));

        assertThat(totals.totalMarketValue).isEqualByComparingTo("300");
    }

    @Test
    void should_accumulateRealizedAndExit_when_addRealizedCloseCalled() {
        SnapshotTotals totals = new SnapshotTotals();

        totals.addRealizedClose(new BigDecimal("15"), new BigDecimal("115"));
        totals.addRealizedClose(new BigDecimal("-5"), new BigDecimal("95"));

        assertThat(totals.cumulativeRealized).isEqualByComparingTo("10");
        assertThat(totals.closedExitValue).isEqualByComparingTo("210");
    }

    @Test
    void should_buildAggregateSnapshotWithExpectedTotals_when_marketAndClosedExitPresent() {
        SnapshotTotals totals = new SnapshotTotals();
        totals.addEntry(new BigDecimal("1000"));
        totals.addMarket(new BigDecimal("800"));
        totals.addRealizedClose(new BigDecimal("250"), new BigDecimal("250"));
        DailyDelta delta = new DailyDelta(new BigDecimal("30"), new BigDecimal("3"));

        var snapshot = totals.toAggregateSnapshot(PORTFOLIO_ID, SNAP_DATE, BATCH_TS, delta);

        assertThat(snapshot.getPortfolioId()).isEqualTo(PORTFOLIO_ID);
        assertThat(snapshot.getSnapshotDate()).isEqualTo(SNAP_DATE);
        assertThat(snapshot.getCreatedAt()).isEqualTo(BATCH_TS);
        // totalValue = open MV + closed-lot exit cash (800 + 250 = 1050) — drives the unfiltered
        // chart which must not dip on close day. cashTry tracks the realized portion separately.
        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo("1050.0000");
        assertThat(snapshot.getCashTry()).isEqualByComparingTo("250.0000");
        assertThat(snapshot.getTotalCostTry()).isEqualByComparingTo("1000.0000");
        assertThat(snapshot.getTotalPnlTry()).isEqualByComparingTo("50.0000");
        assertThat(snapshot.getPnlPercent()).isEqualByComparingTo("5.0000");
        assertThat(snapshot.getDailyPnlTry()).isEqualByComparingTo("30");
        assertThat(snapshot.getDailyPnlPercent()).isEqualByComparingTo("3");
    }

    @Test
    void should_returnZeroPnl_when_entryValueIsZero() {
        SnapshotTotals totals = new SnapshotTotals();
        DailyDelta delta = DailyDelta.EMPTY;

        var snapshot = totals.toAggregateSnapshot(PORTFOLIO_ID, SNAP_DATE, BATCH_TS, delta);

        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getTotalCostTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getTotalPnlTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getPnlPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getDailyPnlTry()).isNull();
        assertThat(snapshot.getDailyPnlPercent()).isNull();
    }

    @Test
    void should_passThroughDailyDelta_when_aggregateSnapshotIsBuilt() {
        SnapshotTotals totals = new SnapshotTotals();
        totals.addEntry(new BigDecimal("100"));
        totals.addMarket(new BigDecimal("110"));
        DailyDelta delta = new DailyDelta(new BigDecimal("5.1234"), new BigDecimal("4.6789"));

        var snapshot = totals.toAggregateSnapshot(PORTFOLIO_ID, SNAP_DATE, BATCH_TS, delta);

        assertThat(snapshot.getDailyPnlTry()).isEqualByComparingTo("5.1234");
        assertThat(snapshot.getDailyPnlPercent()).isEqualByComparingTo("4.6789");
    }

    @Test
    void should_handleNegativePnl_when_marketBelowEntry() {
        SnapshotTotals totals = new SnapshotTotals();
        totals.addEntry(new BigDecimal("1000"));
        totals.addMarket(new BigDecimal("800"));
        DailyDelta delta = DailyDelta.EMPTY;

        var snapshot = totals.toAggregateSnapshot(PORTFOLIO_ID, SNAP_DATE, BATCH_TS, delta);

        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo("800.0000");
        assertThat(snapshot.getTotalPnlTry()).isEqualByComparingTo("-200.0000");
        assertThat(snapshot.getPnlPercent()).isEqualByComparingTo("-20.0000");
    }
}
