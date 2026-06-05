package com.finance.portfolio.service.performance;

import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViopDirectionSeriesRecomputerTest {

    private static final String SYMBOL = "F_USDTRY0626";
    private static final long PORTFOLIO_ID = 1L;

    private ViopDirectionSeriesRecomputer recomputer;

    @BeforeEach
    void setUp() {
        recomputer = new ViopDirectionSeriesRecomputer();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "LONG,    LONG",
            "long,    LONG",
            "  long , LONG",
            "Short,   SHORT",
            "SHORT,   SHORT",
    })
    void should_parse_direction_case_insensitively_when_value_is_valid(String input, DerivativeDirection expected) {
        DerivativeDirection result = recomputer.parseDirectionOrNull(input);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "null",
            "''",
            "'   '",
            "BOGUS",
            "longish",
    })
    void should_return_null_when_direction_is_blank_or_unknown(String input) {
        DerivativeDirection result = recomputer.parseDirectionOrNull(input);

        assertThat(result).isNull();
    }

    @Test
    void should_sum_same_timestamp_rows_when_two_legs_share_one_createdAt() {
        LocalDateTime ts = LocalDateTime.of(2026, 4, 2, 18, 0);
        PortfolioAssetDailySnapshot longRow = row(ts, LocalDate.of(2026, 4, 2), new BigDecimal("35.50"))
                .quantity(new BigDecimal("1"))
                .marketValueTry(new BigDecimal("35500.0000"))
                .totalCostTry(new BigDecimal("35200.0000"))
                .pnlTry(new BigDecimal("300.0000"))
                .dailyPnlTry(new BigDecimal("100.0000"))
                .dailyPnlPercent(new BigDecimal("0.28"))
                .build();
        PortfolioAssetDailySnapshot shortRow = row(ts, LocalDate.of(2026, 4, 2), new BigDecimal("35.50"))
                .quantity(new BigDecimal("2"))
                .marketValueTry(new BigDecimal("71000.0000"))
                .totalCostTry(new BigDecimal("70400.0000"))
                .pnlTry(new BigDecimal("-300.0000"))
                .dailyPnlTry(new BigDecimal("-100.0000"))
                .dailyPnlPercent(new BigDecimal("-0.28"))
                .build();

        List<PortfolioAssetDailySnapshot> merged =
                recomputer.mergeSameTimestampRows(List.of(longRow, shortRow));

        assertThat(merged).hasSize(1);
        PortfolioAssetDailySnapshot only = merged.get(0);
        assertThat(only.getQuantity()).isEqualByComparingTo("3");
        assertThat(only.getMarketValueTry()).isEqualByComparingTo("106500.0000");
        assertThat(only.getTotalCostTry()).isEqualByComparingTo("105600.0000");
        assertThat(only.getPnlTry()).isEqualByComparingTo("0.0000");
        assertThat(only.getDailyPnlTry()).isEqualByComparingTo("0.0000");
        assertThat(only.getUnitPriceTry()).isEqualByComparingTo("35.50");
    }

    @Test
    void should_keep_distinct_timestamps_separate_and_carry_nullable_dailyPnl_when_merging() {
        LocalDateTime tsOne = LocalDateTime.of(2026, 4, 2, 18, 0);
        LocalDateTime tsTwo = LocalDateTime.of(2026, 4, 3, 18, 0);
        PortfolioAssetDailySnapshot first = row(tsOne, LocalDate.of(2026, 4, 2), new BigDecimal("35.50"))
                .quantity(new BigDecimal("1"))
                .marketValueTry(new BigDecimal("35500.0000"))
                .dailyPnlTry(null)
                .build();
        PortfolioAssetDailySnapshot secondLegA = row(tsTwo, LocalDate.of(2026, 4, 3), new BigDecimal("36.00"))
                .quantity(new BigDecimal("1"))
                .marketValueTry(new BigDecimal("36000.0000"))
                .dailyPnlTry(null)
                .build();
        PortfolioAssetDailySnapshot secondLegB = row(tsTwo, LocalDate.of(2026, 4, 3), new BigDecimal("36.00"))
                .quantity(new BigDecimal("1"))
                .marketValueTry(new BigDecimal("36000.0000"))
                .dailyPnlTry(new BigDecimal("250.0000"))
                .build();

        List<PortfolioAssetDailySnapshot> merged =
                recomputer.mergeSameTimestampRows(List.of(first, secondLegA, secondLegB));

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).getCreatedAt()).isEqualTo(tsOne);
        assertThat(merged.get(0).getDailyPnlTry()).isNull();
        assertThat(merged.get(1).getCreatedAt()).isEqualTo(tsTwo);
        assertThat(merged.get(1).getQuantity()).isEqualByComparingTo("2");
        assertThat(merged.get(1).getDailyPnlTry()).isEqualByComparingTo("250.0000");
    }

    @Test
    void should_skip_point_when_unit_price_or_snapshot_date_is_null() {
        PortfolioAssetDailySnapshot noUnit = row(LocalDateTime.of(2026, 4, 1, 18, 0),
                LocalDate.of(2026, 4, 1), null).build();
        PortfolioAssetDailySnapshot noDate = PortfolioAssetDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID)
                .assetType(AssetType.VIOP)
                .assetCode(SYMBOL)
                .createdAt(LocalDateTime.of(2026, 4, 2, 18, 0))
                .unitPriceTry(new BigDecimal("35.50"))
                .build();
        DerivativePosition longLot = longLot(LocalDate.of(2026, 4, 1));

        List<PortfolioAssetDailySnapshot> out =
                recomputer.recomputeDirectionViopSeries(List.of(noUnit, noDate), List.of(longLot));

        assertThat(out).isEmpty();
    }

    @Test
    void should_reconstruct_long_series_with_no_daily_on_entry_day_and_daily_pct_next_day() {
        // Contract size 10, LONG 2 lots entered 35.00 on day1. Shared unitPrice: day1 35.50, day2 36.00.
        // Day1 = entry day -> no daily (anyDaily false). Day2 held across the day -> daily computed.
        // Per-lot daily = (36.00 - 35.50) * 10 = 5.00; x 2 lots = 10.00 TRY.
        // prevMarketValue (day1) = 35.50 * 10 * 2 = 710.0000 -> pct = 10 * 100 / 710 = 1.4085 (4dp HALF_UP).
        LocalDate day1 = LocalDate.of(2026, 4, 1);
        LocalDate day2 = LocalDate.of(2026, 4, 2);
        PortfolioAssetDailySnapshot snap1 = row(day1.atTime(18, 0), day1, new BigDecimal("35.50")).build();
        PortfolioAssetDailySnapshot snap2 = row(day2.atTime(18, 0), day2, new BigDecimal("36.00")).build();
        DerivativePosition longLot = DerivativePosition.builder()
                .id(10L)
                .direction(DerivativeDirection.LONG)
                .entryDate(day1)
                .entryPrice(new BigDecimal("35.00"))
                .quantityLot(new BigDecimal("2"))
                .viopContract(contract(new BigDecimal("10")))
                .build();

        List<PortfolioAssetDailySnapshot> out =
                recomputer.recomputeDirectionViopSeries(List.of(snap1, snap2), List.of(longLot));

        assertThat(out).hasSize(2);
        PortfolioAssetDailySnapshot d1 = out.get(0);
        assertThat(d1.getAssetType()).isEqualTo(AssetType.VIOP);
        assertThat(d1.getAssetCode()).isEqualTo(SYMBOL);
        assertThat(d1.getQuantity()).isEqualByComparingTo("2");
        assertThat(d1.getMarketValueTry()).isEqualByComparingTo("710.0000");   // 35.50 * 10 * 2
        assertThat(d1.getTotalCostTry()).isEqualByComparingTo("700.0000");     // 35.00 * 10 * 2
        assertThat(d1.getPnlTry()).isEqualByComparingTo("10.0000");            // (35.50-35.00)*10*2
        assertThat(d1.getDailyPnlTry()).isNull();
        assertThat(d1.getDailyPnlPercent()).isNull();
        PortfolioAssetDailySnapshot d2 = out.get(1);
        assertThat(d2.getDailyPnlTry()).isEqualByComparingTo("10.0000");       // (36.00-35.50)*10*2
        assertThat(d2.getDailyPnlPercent()).isEqualByComparingTo("1.4085");    // 10*100/710
        assertThat(d2.getMarketValueTry()).isEqualByComparingTo("720.0000");
    }

    @Test
    void should_skip_short_lot_on_and_after_its_close_day_and_drop_lots_not_yet_opened() {
        // SHORT lot opened day2, closed day3. Series spans day1..day4 with one shared unitPrice each.
        //  day1: lot not opened yet -> no row.
        //  day2: entry day -> row, no daily.
        //  day3: close day -> "!closeDate.isAfter(date)" true -> skipped -> no row (else branch, prevHadLots=false).
        //  day4: closed before date -> skipped -> no row.
        LocalDate day1 = LocalDate.of(2026, 4, 1);
        LocalDate day2 = LocalDate.of(2026, 4, 2);
        LocalDate day3 = LocalDate.of(2026, 4, 3);
        LocalDate day4 = LocalDate.of(2026, 4, 4);
        List<PortfolioAssetDailySnapshot> merged = List.of(
                row(day1.atTime(18, 0), day1, new BigDecimal("35.00")).build(),
                row(day2.atTime(18, 0), day2, new BigDecimal("35.40")).build(),
                row(day3.atTime(18, 0), day3, new BigDecimal("35.80")).build(),
                row(day4.atTime(18, 0), day4, new BigDecimal("36.00")).build());
        DerivativePosition shortLot = DerivativePosition.builder()
                .id(20L)
                .direction(DerivativeDirection.SHORT)
                .entryDate(day2)
                .entryPrice(new BigDecimal("35.40"))
                .quantityLot(new BigDecimal("1"))
                .viopContract(contract(new BigDecimal("10")))
                .build();
        shortLot.setCloseDate(day3);

        List<PortfolioAssetDailySnapshot> out =
                recomputer.recomputeDirectionViopSeries(merged, List.of(shortLot));

        assertThat(out).hasSize(1);
        PortfolioAssetDailySnapshot only = out.get(0);
        assertThat(only.getSnapshotDate()).isEqualTo(day2);
        assertThat(only.getDailyPnlTry()).isNull();   // entry day, contribution-immune
        assertThat(only.getPnlTry()).isEqualByComparingTo("0.0000");   // SHORT entry==mark on day2
    }

    @Test
    void should_emit_daily_pct_on_reopen_held_day_when_reopen_entry_day_seeds_prev_market_value() {
        // One lot closes (day2 -> skipped on/after), the direction is fully closed on day3 (gap, no row,
        // prevHadLots=false), then a NEW lot reopens day4 with a different size. day4 is the reopen ENTRY
        // day (no daily, but it seeds prevMarketValue & prevHadLots=true). day5 is held across -> daily PnL
        // AND a valid % off the FRESH day4 market value (not the stale pre-gap one).
        LocalDate day1 = LocalDate.of(2026, 4, 1);
        LocalDate day2 = LocalDate.of(2026, 4, 2);
        LocalDate day3 = LocalDate.of(2026, 4, 3);
        LocalDate day4 = LocalDate.of(2026, 4, 4);
        LocalDate day5 = LocalDate.of(2026, 4, 5);
        List<PortfolioAssetDailySnapshot> merged = List.of(
                row(day1.atTime(18, 0), day1, new BigDecimal("35.00")).build(),
                row(day2.atTime(18, 0), day2, new BigDecimal("35.50")).build(),
                row(day3.atTime(18, 0), day3, new BigDecimal("35.80")).build(),   // gap: no lot open
                row(day4.atTime(18, 0), day4, new BigDecimal("36.00")).build(),   // reopen entry day
                row(day5.atTime(18, 0), day5, new BigDecimal("36.40")).build());  // first held-across after gap
        DerivativePosition firstLot = DerivativePosition.builder()
                .id(30L).direction(DerivativeDirection.LONG)
                .entryDate(day1).entryPrice(new BigDecimal("35.00")).quantityLot(new BigDecimal("1"))
                .viopContract(contract(new BigDecimal("10"))).build();
        firstLot.setCloseDate(day2);   // skipped on/after day2
        DerivativePosition reopened = DerivativePosition.builder()
                .id(31L).direction(DerivativeDirection.LONG)
                .entryDate(day4).entryPrice(new BigDecimal("36.00")).quantityLot(new BigDecimal("5"))
                .viopContract(contract(new BigDecimal("10"))).build();

        List<PortfolioAssetDailySnapshot> out =
                recomputer.recomputeDirectionViopSeries(merged, List.of(firstLot, reopened));

        // day1 (entry, lot1), day4 (reopen entry), day5 (held across) -> 3 rows. day2/day3 produce none.
        assertThat(out).extracting(PortfolioAssetDailySnapshot::getSnapshotDate)
                .containsExactly(day1, day4, day5);
        PortfolioAssetDailySnapshot reopenEntry = out.get(1);
        assertThat(reopenEntry.getSnapshotDate()).isEqualTo(day4);
        assertThat(reopenEntry.getDailyPnlTry()).isNull();              // its own entry day
        assertThat(reopenEntry.getMarketValueTry()).isEqualByComparingTo("1800.0000");   // 36.00 * 10 * 5
        PortfolioAssetDailySnapshot firstHeldAfterGap = out.get(2);
        assertThat(firstHeldAfterGap.getSnapshotDate()).isEqualTo(day5);
        assertThat(firstHeldAfterGap.getDailyPnlTry()).isEqualByComparingTo("20.0000");   // (36.40-36.00)*10*5
        assertThat(firstHeldAfterGap.getDailyPnlPercent()).isEqualByComparingTo("1.1111"); // 20*100/1800, fresh base
    }

    @Test
    void should_suppress_daily_pct_when_prevHadLots_false_despite_a_held_across_day() {
        // The prevHadLots guard: a null-DATE snapshot is skipped via "continue" which sets prevUnit but
        // never touches prevHadLots (stays false). The NEXT day the lot is held across (prevUnit non-null,
        // entry != date) so a daily PnL is produced -> but with prevHadLots still false the % is suppressed.
        LocalDate day1 = LocalDate.of(2026, 4, 1);
        LocalDate day2 = LocalDate.of(2026, 4, 2);
        PortfolioAssetDailySnapshot nullDateButPriced = PortfolioAssetDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID).assetType(AssetType.VIOP).assetCode(SYMBOL)
                .createdAt(day1.atTime(18, 0))
                .unitPriceTry(new BigDecimal("35.50"))   // sets prevUnit on the null-date skip
                .build();
        PortfolioAssetDailySnapshot day2Row = row(day2.atTime(18, 0), day2, new BigDecimal("36.00")).build();
        DerivativePosition longLot = DerivativePosition.builder()
                .id(35L).direction(DerivativeDirection.LONG)
                .entryDate(day1).entryPrice(new BigDecimal("35.00")).quantityLot(new BigDecimal("2"))
                .viopContract(contract(new BigDecimal("10"))).build();

        List<PortfolioAssetDailySnapshot> out =
                recomputer.recomputeDirectionViopSeries(List.of(nullDateButPriced, day2Row), List.of(longLot));

        assertThat(out).hasSize(1);
        PortfolioAssetDailySnapshot only = out.get(0);
        assertThat(only.getSnapshotDate()).isEqualTo(day2);
        assertThat(only.getDailyPnlTry()).isEqualByComparingTo("10.0000");   // (36.00-35.50)*10*2, prevUnit from skip
        assertThat(only.getDailyPnlPercent()).isNull();                      // prevHadLots false -> % suppressed
    }

    @Test
    void should_skip_lots_with_null_direction_and_default_contract_size_to_one() {
        // A lot with null direction is ignored. A lot with a null contractSize defaults size to ONE, so
        // marketValue = unit * 1 * qty.
        LocalDate day1 = LocalDate.of(2026, 4, 1);
        PortfolioAssetDailySnapshot snap = row(day1.atTime(18, 0), day1, new BigDecimal("40.00")).build();
        DerivativePosition nullDirection = DerivativePosition.builder()
                .id(40L).direction(null)
                .entryDate(day1).entryPrice(new BigDecimal("38.00")).quantityLot(new BigDecimal("1"))
                .viopContract(contract(new BigDecimal("10"))).build();
        DerivativePosition nullSizeLot = DerivativePosition.builder()
                .id(41L).direction(DerivativeDirection.LONG)
                .entryDate(day1).entryPrice(new BigDecimal("38.00")).quantityLot(new BigDecimal("3"))
                .viopContract(contract(null)).build();

        List<PortfolioAssetDailySnapshot> out =
                recomputer.recomputeDirectionViopSeries(List.of(snap), List.of(nullDirection, nullSizeLot));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getQuantity()).isEqualByComparingTo("3");
        assertThat(out.get(0).getMarketValueTry()).isEqualByComparingTo("120.0000");   // 40 * 1 * 3
        assertThat(out.get(0).getTotalCostTry()).isEqualByComparingTo("114.0000");     // 38 * 1 * 3
    }

    @Test
    void should_return_empty_series_when_no_lot_is_open_on_any_snapshot_date() {
        // Lot opens in the future relative to every snapshot date -> never any lot -> empty out.
        LocalDate day1 = LocalDate.of(2026, 4, 1);
        PortfolioAssetDailySnapshot snap = row(day1.atTime(18, 0), day1, new BigDecimal("35.50")).build();
        DerivativePosition futureLot = longLot(LocalDate.of(2026, 5, 1));

        List<PortfolioAssetDailySnapshot> out =
                recomputer.recomputeDirectionViopSeries(List.of(snap), List.of(futureLot));

        assertThat(out).isEmpty();
    }

    private PortfolioAssetDailySnapshot.PortfolioAssetDailySnapshotBuilder row(
            LocalDateTime createdAt, LocalDate snapshotDate, BigDecimal unitPriceTry) {
        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID)
                .assetType(AssetType.VIOP)
                .assetCode(SYMBOL)
                .snapshotDate(snapshotDate)
                .createdAt(createdAt)
                .unitPriceTry(unitPriceTry);
    }

    private DerivativePosition longLot(LocalDate entryDate) {
        return DerivativePosition.builder()
                .id(99L)
                .direction(DerivativeDirection.LONG)
                .entryDate(entryDate)
                .entryPrice(new BigDecimal("35.00"))
                .quantityLot(new BigDecimal("1"))
                .viopContract(contract(new BigDecimal("10")))
                .build();
    }

    private ViopContract contract(BigDecimal contractSize) {
        return ViopContract.builder()
                .symbol(SYMBOL)
                .kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY)
                .contractSize(contractSize)
                .currency("TRY")
                .active(true)
                .build();
    }
}
