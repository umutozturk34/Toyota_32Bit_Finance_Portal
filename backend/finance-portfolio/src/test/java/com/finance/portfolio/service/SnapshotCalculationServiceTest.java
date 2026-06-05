package com.finance.portfolio.service;
import com.finance.shared.service.AssetPricingPort;



import com.finance.portfolio.model.AssetType;
import com.finance.common.model.MarketType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.service.support.CountingAssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotCalculationServiceTest {

    @Mock(answer = Answers.CALLS_REAL_METHODS) private AssetPricingPort pricingPort;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private com.finance.portfolio.derivative.repository.DerivativePositionRepository derivativePositionRepository;
    @Mock private PortfolioDailySnapshotRepository dailySnapshotRepository;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;

    private SnapshotCalculationService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotCalculationService(pricingPort, positionRepository, derivativePositionRepository,
                dailySnapshotRepository, assetSnapshotRepository, new PortfolioProperties(),
                new DerivativeSnapshotAssembler(pricingPort, assetSnapshotRepository, org.mockito.Mockito.mock(com.finance.market.core.service.HistoricalPricingPort.class)));
        org.mockito.Mockito.lenient().when(derivativePositionRepository.findByPortfolioId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(assetSnapshotRepository.findLatestPerAsset(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void shouldCalculatePnlFromCurrentVsEntryPrice_whenBuildingAssetSnapshot() {
        PortfolioPosition pos = stubPosition(AssetType.CRYPTO, "bitcoin",
                new BigDecimal("0.50000000"), new BigDecimal("2500000.0000"));
        when(pricingPort.getPriceTry(MarketType.CRYPTO, "bitcoin"))
                .thenReturn(new BigDecimal("2600000.0000"));
        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 10, 23, 0);

        PortfolioAssetDailySnapshot snapshot = service
                .buildAssetSnapshotsForPositions(1L, List.of(pos), timestamp).get(0);

        assertThat(snapshot.getMarketValueTry()).isEqualByComparingTo(new BigDecimal("1300000.0000"));
        assertThat(snapshot.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("1250000.0000"));
        assertThat(snapshot.getPnlTry()).isEqualByComparingTo(new BigDecimal("50000.0000"));
        assertThat(snapshot.getUnitPriceTry()).isEqualByComparingTo(new BigDecimal("2600000.0000"));
        assertThat(snapshot.getSnapshotDate()).isEqualTo(timestamp.toLocalDate());
        assertThat(snapshot.getCreatedAt()).isEqualTo(timestamp);
    }

    @Test
    void shouldShowFullLossInAssetSnapshot_whenPriceIsNull() {
        PortfolioPosition pos = stubPosition(AssetType.STOCK, "DELISTED",
                new BigDecimal("100.00000000"), new BigDecimal("50.0000"));
        when(pricingPort.getPriceTry(MarketType.STOCK, "DELISTED")).thenReturn(null);

        PortfolioAssetDailySnapshot snapshot = service
                .buildAssetSnapshotsForPositions(1L, List.of(pos), LocalDateTime.now()).get(0);

        assertThat(snapshot.getUnitPriceTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getMarketValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getPnlTry()).isEqualByComparingTo(new BigDecimal("-5000.0000"));
    }

    @Test
    void shouldSumLotsOfSameAsset_whenBuildingAssetSnapshotsForPositions() {
        PortfolioPosition lotA = stubPosition(AssetType.FUND, "BND",
                new BigDecimal("100"), new BigDecimal("3.00"));
        PortfolioPosition lotB = stubPosition(AssetType.FUND, "BND",
                new BigDecimal("50"), new BigDecimal("3.10"));
        when(pricingPort.getPriceTry(MarketType.FUND, "BND")).thenReturn(new BigDecimal("3.20"));

        List<PortfolioAssetDailySnapshot> snapshots = service
                .buildAssetSnapshotsForPositions(1L, List.of(lotA, lotB), LocalDateTime.now());

        assertThat(snapshots).hasSize(1);
        PortfolioAssetDailySnapshot snap = snapshots.get(0);
        assertThat(snap.getQuantity()).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(snap.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("455.0000"));
        assertThat(snap.getMarketValueTry()).isEqualByComparingTo(new BigDecimal("480.0000"));
        assertThat(snap.getPnlTry()).isEqualByComparingTo(new BigDecimal("25.0000"));
    }

    @Test
    void shouldSumAllPositions_whenBuildingAggregateSnapshot() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1.00000000"), new BigDecimal("2400000.0000")),
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100.00000000"), new BigDecimal("40.0000"))));
        when(pricingPort.getPriceTry(MarketType.CRYPTO, "bitcoin"))
                .thenReturn(new BigDecimal("2500000.0000"));
        when(pricingPort.getPriceTry(MarketType.STOCK, "THYAO.IS"))
                .thenReturn(new BigDecimal("50.0000"));

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(new BigDecimal("2505000.0000"));
        assertThat(snapshot.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("2404000.0000"));
        assertThat(snapshot.getTotalPnlTry()).isEqualByComparingTo(new BigDecimal("101000.0000"));
    }

    @Test
    void shouldComputePnlPercentRelativeToEntryValue_whenBuildingAggregateSnapshot() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(stubPosition(AssetType.FUND, "AAK",
                        new BigDecimal("100.00000000"), new BigDecimal("100.0000"))));
        when(pricingPort.getPriceTry(MarketType.FUND, "AAK"))
                .thenReturn(new BigDecimal("110.0000"));

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(snapshot.getPnlPercent()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void shouldIssueExactlyOneBatchPricingCall_whenBuildingAggregateSnapshot() {
        CountingAssetPricingPort counting = new CountingAssetPricingPort();
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("2500000.0000"));
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("50.0000"));
        counting.seedPrice("FUND", "AAK", new BigDecimal("110.0000"));

        SnapshotCalculationService countedService = new SnapshotCalculationService(counting, positionRepository, derivativePositionRepository,
                dailySnapshotRepository, assetSnapshotRepository, new PortfolioProperties(),
                new DerivativeSnapshotAssembler(counting, assetSnapshotRepository, org.mockito.Mockito.mock(com.finance.market.core.service.HistoricalPricingPort.class)));

        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1.00000000"), new BigDecimal("2400000.0000")),
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100.00000000"), new BigDecimal("40.0000")),
                        stubPosition(AssetType.FUND, "AAK", new BigDecimal("50.00000000"), new BigDecimal("100.0000"))));

        countedService.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(counting.batchPricesCalls()).isEqualTo(1);
        assertThat(counting.priceCalls()).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroAggregate_whenNoPositionsExist() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(snapshot.getPnlPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getTotalCostTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void buildAssetSnapshots_booksCloseDayMoveAndExitEquity_whenSpotFullySoldToday() {
        com.finance.common.model.TrackedAsset tracked = com.finance.common.model.TrackedAsset.builder()
                .id(7L).assetType(com.finance.common.model.TrackedAssetType.STOCK).assetCode("THYAO.IS").build();
        PortfolioPosition sold = PortfolioPosition.builder()
                .assetType(AssetType.STOCK).assetCode("THYAO.IS").trackedAsset(tracked)
                .quantity(new BigDecimal("100")).entryPrice(new BigDecimal("40.0000"))
                .entryDate(LocalDateTime.of(2026, 4, 1, 0, 0)).build();
        sold.closeWith(LocalDateTime.of(2026, 6, 5, 10, 0), new BigDecimal("42.7500"));
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .quantity(new BigDecimal("100")).unitPriceTry(new BigDecimal("40.0000")).build();
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(prior));

        PortfolioAssetDailySnapshot row = service.buildAssetSnapshotsForPositions(
                1L, List.of(sold), LocalDateTime.of(2026, 6, 5, 18, 0)).get(0);

        // Sold in full today: close-day move = 100 × (42.75 − 40) = 275 is booked as the day's K/Z (it was 0
        // before — a closed lot received no per-asset row); value = realized exit (100 × 42.75 = 4275).
        assertThat(row.getDailyPnlTry()).isEqualByComparingTo("275.0000");
        assertThat(row.getMarketValueTry()).isEqualByComparingTo("4275.0000");
    }

    @Test
    void buildAssetSnapshots_addsSoldPortionMoveToDaily_butKeepsValueOpenOnly_whenSpotPartiallySoldToday() {
        com.finance.common.model.TrackedAsset tracked = com.finance.common.model.TrackedAsset.builder()
                .id(7L).assetType(com.finance.common.model.TrackedAssetType.STOCK).assetCode("THYAO.IS").build();
        PortfolioPosition openHalf = PortfolioPosition.builder()
                .assetType(AssetType.STOCK).assetCode("THYAO.IS").trackedAsset(tracked)
                .quantity(new BigDecimal("50")).entryPrice(new BigDecimal("40.0000"))
                .entryDate(LocalDateTime.of(2026, 4, 1, 0, 0)).build();
        PortfolioPosition soldHalf = PortfolioPosition.builder()
                .assetType(AssetType.STOCK).assetCode("THYAO.IS").trackedAsset(tracked)
                .quantity(new BigDecimal("50")).entryPrice(new BigDecimal("40.0000"))
                .entryDate(LocalDateTime.of(2026, 4, 1, 0, 0)).build();
        soldHalf.closeWith(LocalDateTime.of(2026, 6, 5, 10, 0), new BigDecimal("42.7500"));
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .quantity(new BigDecimal("100")).unitPriceTry(new BigDecimal("40.0000")).build();
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(prior));
        when(pricingPort.getPriceTry(MarketType.STOCK, "THYAO.IS")).thenReturn(new BigDecimal("42.7500"));

        List<PortfolioAssetDailySnapshot> rows = service.buildAssetSnapshotsForPositions(
                1L, List.of(openHalf, soldHalf), LocalDateTime.of(2026, 6, 5, 18, 0));

        assertThat(rows).hasSize(1);
        PortfolioAssetDailySnapshot row = rows.get(0);
        // Daily = held-half move (50 × 2.75 = 137.5) + sold-half close-day move (50 × 2.75 = 137.5) = 275, the
        // full position's day move; the headline card and the detail series both read THIS row, so they agree.
        assertThat(row.getDailyPnlTry()).isEqualByComparingTo("275.0000");
        // Value stays open-only (50 × 42.75 = 2137.5): the sold half's realized exit is folded by the aggregate,
        // so counting it on this row too would double the value.
        assertThat(row.getMarketValueTry()).isEqualByComparingTo("2137.5000");
    }

    private PortfolioPosition stubPosition(AssetType type, String code, BigDecimal qty, BigDecimal entryPrice) {
        return PortfolioPosition.builder()
                .assetType(type)
                .assetCode(code)
                .quantity(qty)
                .entryPrice(entryPrice)
                .entryDate(LocalDateTime.now())
                .build();
    }

    private com.finance.market.viop.model.ViopContract derivativeContract(String symbol,
                                                                          BigDecimal contractSize,
                                                                          BigDecimal lastPrice) {
        return com.finance.market.viop.model.ViopContract.builder()
                .symbol(symbol)
                .kind(com.finance.market.viop.model.ViopContractKind.FUTURE)
                .contractSize(contractSize)
                .currency("TRY")
                .lastPrice(lastPrice)
                .active(true)
                .build();
    }

    private com.finance.portfolio.derivative.model.DerivativePosition derivativePosition(
            com.finance.market.viop.model.ViopContract contract, BigDecimal entry, BigDecimal qty,
            com.finance.portfolio.derivative.model.DerivativeDirection direction) {
        return com.finance.portfolio.derivative.model.DerivativePosition.builder()
                .id(10L)
                .direction(direction)
                .entryDate(java.time.LocalDate.of(2026, 4, 1))
                .entryPrice(entry)
                .quantityLot(qty)
                .viopContract(contract)
                .build();
    }

    @Test
    void should_buildDerivativeSnapshotWithLivePrice_whenPositionIsOpen() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("35.20"), new BigDecimal("2"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos,
                LocalDateTime.of(2026, 5, 1, 18, 0));

        assertThat(snap).isNotNull();
        assertThat(snap.getAssetType()).isEqualTo(AssetType.VIOP);
        assertThat(snap.getAssetCode()).isEqualTo("F_USDTRY0626");
        assertThat(snap.getUnitPriceTry()).isEqualByComparingTo("35.5000");
        assertThat(snap.getMarketValueTry()).isEqualByComparingTo("71000.0000");
        assertThat(snap.getTotalCostTry()).isEqualByComparingTo("70400.0000");
        assertThat(snap.getPnlTry()).isEqualByComparingTo("600.0000");
    }

    @Test
    void should_freezeAtClosePrice_whenPositionIsClosed() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("99.99"));
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("35.20"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        pos.closeWith(java.time.LocalDate.of(2026, 5, 1), new BigDecimal("36.00"),
                com.finance.portfolio.derivative.model.DerivativeCloseReason.USER_CLOSED);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos, LocalDateTime.now());

        assertThat(snap.getUnitPriceTry()).isEqualByComparingTo("36.0000");
        assertThat(snap.getPnlTry()).isEqualByComparingTo("800.0000");
    }

    @Test
    void aggregateValue_staysWhole_whenViopPartiallySoldToday_withOpenRowPlusQtyZeroCloseRow() {
        // REAL partial-close shape on the sell day: snapshotDerivativePositions writes an OPEN remainder row
        // (countable, qty 0.5) AND a value-less qty=0 close-day row. The aggregate MUST be open equity + closed
        // proceeds = the WHOLE position (110), NOT doubled (165) — this pins the "Tümü TRY doubles on sell day".
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_XAUUSD0826", BigDecimal.ONE, new BigDecimal("110"));
        com.finance.portfolio.derivative.model.DerivativePosition openRemainder = derivativePosition(
                c, new BigDecimal("100"), new BigDecimal("0.5"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        com.finance.portfolio.derivative.model.DerivativePosition closedSlice = derivativePosition(
                c, new BigDecimal("100"), new BigDecimal("0.5"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        closedSlice.closeWith(java.time.LocalDate.of(2026, 6, 2), new BigDecimal("110"),
                com.finance.portfolio.derivative.model.DerivativeCloseReason.USER_CLOSED);
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        PortfolioAssetDailySnapshot openRow = PortfolioAssetDailySnapshot.builder()
                .portfolioId(1L).assetType(AssetType.VIOP).assetCode("F_XAUUSD0826")
                .snapshotDate(java.time.LocalDate.of(2026, 6, 2)).createdAt(LocalDateTime.of(2026, 6, 2, 18, 0))
                .quantity(new BigDecimal("0.5")).unitPriceTry(new BigDecimal("110"))
                .marketValueTry(new BigDecimal("55")).totalCostTry(new BigDecimal("50"))
                .pnlTry(new BigDecimal("5")).build();
        PortfolioAssetDailySnapshot zeroRow = PortfolioAssetDailySnapshot.builder()
                .portfolioId(1L).assetType(AssetType.VIOP).assetCode("F_XAUUSD0826")
                .snapshotDate(java.time.LocalDate.of(2026, 6, 2)).createdAt(LocalDateTime.of(2026, 6, 2, 18, 0))
                .quantity(BigDecimal.ZERO).unitPriceTry(new BigDecimal("110"))
                .marketValueTry(BigDecimal.ZERO).totalCostTry(BigDecimal.ZERO).pnlTry(BigDecimal.ZERO).build();

        PortfolioDailySnapshot snap = service.buildAggregateSnapshotAtFromRows(
                portfolio, LocalDateTime.of(2026, 6, 2, 18, 0),
                java.util.List.of(), java.util.List.of(openRemainder, closedSlice),
                java.util.Map.of(), java.util.List.of(openRow, zeroRow));

        assertThat(snap.getTotalValueTry()).isEqualByComparingTo(new BigDecimal("110"));
    }

    @Test
    void buildClosedViopDailyRow_carriesCloseDayMove_butZerosValueFields() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("36.00"));
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("35.20"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        pos.closeWith(java.time.LocalDate.of(2026, 6, 5), new BigDecimal("36.00"),
                com.finance.portfolio.derivative.model.DerivativeCloseReason.USER_CLOSED);
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .unitPriceTry(new BigDecimal("35.50")).marketValueTry(new BigDecimal("35500")).build();
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(AssetType.VIOP),
                        org.mockito.ArgumentMatchers.eq("F_USDTRY0626"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(prior));

        PortfolioAssetDailySnapshot row = service.buildClosedViopDailyRow(
                1L, pos, LocalDateTime.of(2026, 6, 5, 18, 0));

        // Close-day move = (36.00 − 35.50) × 1000 × 1 = 500 (LONG) booked in dailyPnlTry so the Günlük K/Z card
        // sees today's move; value fields zeroed → isCountableViopRow=false, so the row never enters the value
        // path and the proceeds are counted once via addClosedEquity (no double-count).
        assertThat(row.getDailyPnlTry()).isEqualByComparingTo("500.0000");
        assertThat(row.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.getMarketValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.getPnlTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_invertPnl_whenShortPositionPriceGoesUp() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("35.20"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.SHORT);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos, LocalDateTime.now());

        assertThat(snap.getPnlTry()).isEqualByComparingTo("-300.0000");
    }

    @Test
    void aggregate_doesNotDoubleCountViopOnCloseDay_whenLotPartiallyClosed() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_TEST0626", new BigDecimal("1"), new BigDecimal("110"));
        com.finance.portfolio.derivative.model.DerivativePosition lot1 = derivativePosition(
                c, new BigDecimal("100"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        lot1.closeWith(java.time.LocalDate.of(2026, 5, 1), new BigDecimal("110"),
                com.finance.portfolio.derivative.model.DerivativeCloseReason.USER_CLOSED);
        com.finance.portfolio.derivative.model.DerivativePosition lot2 = derivativePosition(
                c, new BigDecimal("100"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        // On a partial close, snapshotToday writes ONLY the OPEN remainder's row (the closed slice gets no today
        // row), so the symbol's rowMv is the open-lot notional (110), not both lots (220). The closed-on-snapDate
        // slice must fold its proceeds via addClosedEquity and drop out of the open-lot aggregate; otherwise the
        // open lot's equity allocation is halved (totalLots doubled) AND the proceeds are dropped — the 202k→101k
        // "Tümü" halving. Correct total = 110 open + 110 closed proceeds = 220 (under the old logic this read 110).
        PortfolioAssetDailySnapshot openLotRow = PortfolioAssetDailySnapshot.builder()
                .portfolioId(1L).assetType(AssetType.VIOP).assetCode("F_TEST0626")
                .snapshotDate(java.time.LocalDate.of(2026, 5, 1))
                .createdAt(LocalDateTime.of(2026, 5, 1, 0, 0))
                .quantity(new BigDecimal("1")).unitPriceTry(new BigDecimal("110"))
                .marketValueTry(new BigDecimal("110")).totalCostTry(new BigDecimal("100"))
                .pnlTry(new BigDecimal("10")).build();

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshotAtFromRows(
                portfolio, LocalDateTime.of(2026, 5, 1, 18, 0),
                java.util.List.of(), java.util.List.of(lot1, lot2),
                java.util.Map.of(), java.util.List.of(openLotRow));

        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(new BigDecimal("220"));
    }

    @Test
    void aggregate_includesClosedViopExitValueInTotal_whenLotPartiallyClosed() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_TEST0626", new BigDecimal("1"), new BigDecimal("110"));
        com.finance.portfolio.derivative.model.DerivativePosition lot1 = derivativePosition(
                c, new BigDecimal("100"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        lot1.closeWith(java.time.LocalDate.of(2026, 5, 1), new BigDecimal("110"),
                com.finance.portfolio.derivative.model.DerivativeCloseReason.USER_CLOSED);
        com.finance.portfolio.derivative.model.DerivativePosition lot2 = derivativePosition(
                c, new BigDecimal("100"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        PortfolioAssetDailySnapshot lot2Row = PortfolioAssetDailySnapshot.builder()
                .portfolioId(1L).assetType(AssetType.VIOP).assetCode("F_TEST0626")
                .snapshotDate(java.time.LocalDate.of(2026, 5, 2))
                .createdAt(LocalDateTime.of(2026, 5, 2, 0, 0))
                .quantity(new BigDecimal("1")).unitPriceTry(new BigDecimal("110"))
                .marketValueTry(new BigDecimal("110")).totalCostTry(new BigDecimal("100"))
                .pnlTry(new BigDecimal("10")).build();

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshotAtFromRows(
                portfolio, LocalDateTime.of(2026, 5, 2, 12, 0),
                java.util.List.of(), java.util.List.of(lot1, lot2),
                java.util.Map.of(), java.util.List.of(lot2Row));

        // totalValue = open lot MV (110) + closed lot's exit value (110) = 220. Keeps the
        // unfiltered "Tümü" chart continuous on close day — the realized cash stays in totalValue
        // as a cash bucket, and cashTry mirrors the realised PnL portion (10) for the dedicated card.
        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(new BigDecimal("220"));
        assertThat(snapshot.getCashTry()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void aggregate_keepsExitValueInTotal_onPeerlessViopClose() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "O_EREGLE0526C35.00", new BigDecimal("1"), new BigDecimal("1.14"));
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("2.19"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        pos.closeWith(java.time.LocalDate.of(2026, 5, 24), new BigDecimal("1.14"),
                com.finance.portfolio.derivative.model.DerivativeCloseReason.USER_CLOSED);
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        PortfolioAssetDailySnapshot zeroRow = PortfolioAssetDailySnapshot.builder()
                .portfolioId(1L).assetType(AssetType.VIOP).assetCode("O_EREGLE0526C35.00")
                .snapshotDate(java.time.LocalDate.of(2026, 5, 24))
                .createdAt(LocalDateTime.of(2026, 5, 24, 0, 0, 1))
                .quantity(BigDecimal.ZERO).unitPriceTry(new BigDecimal("1.14"))
                .marketValueTry(BigDecimal.ZERO).totalCostTry(BigDecimal.ZERO)
                .pnlTry(BigDecimal.ZERO).build();

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshotAtFromRows(
                portfolio, LocalDateTime.of(2026, 5, 24, 18, 0),
                java.util.List.of(), java.util.List.of(pos),
                java.util.Map.of(), java.util.List.of(zeroRow));

        // No open positions left but the exit value (1.14) remains in totalValue as a cash bucket
        // so the unfiltered chart line stays continuous. cashTry carries the realised loss (-1.05).
        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(new BigDecimal("1.14"));
        assertThat(snapshot.getCashTry()).isEqualByComparingTo(new BigDecimal("-1.05"));
    }

    @Test
    void should_returnNullSnapshot_whenContractMissingOnDerivative() {
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                null, new BigDecimal("35.20"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos, LocalDateTime.now());

        assertThat(snap).isNull();
    }

    @Test
    void should_useFxRateOverride_whenBuildingDerivativeSnapshotAt() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_X", new BigDecimal("100"), null);
        c.setCurrency("USD");
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("100.00"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshotAt(1L, pos,
                LocalDateTime.now(), new BigDecimal("3.50"), new BigDecimal("32.00"));

        assertThat(snap.getUnitPriceTry()).isEqualByComparingTo("112.0000");
    }

    @Test
    void should_resolveFxFromPricingPort_whenDerivativeIsForeignAndNoFxOverride() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USD", new BigDecimal("100"), new BigDecimal("3.50"));
        c.setCurrency("USD");
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("100.00"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        when(pricingPort.getPriceTry(MarketType.FOREX, "USD")).thenReturn(new BigDecimal("30.00"));

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos,
                LocalDateTime.now());

        assertThat(snap.getUnitPriceTry()).isEqualByComparingTo("105.0000");
    }

    @Test
    void should_returnNullSnapshot_whenForeignContractAndPricingPortReturnsNonPositive() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USD", new BigDecimal("1"), new BigDecimal("100.00"));
        c.setCurrency("USD");
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("100.00"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        when(pricingPort.getPriceTry(MarketType.FOREX, "USD")).thenReturn(BigDecimal.ZERO);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos,
                LocalDateTime.now());

        // No FX → null snapshot (caller skips). Previous "fallback to 1" persisted native USD
        // as TRY, a ~30x value error on USD-denominated derivatives during scraper outages.
        assertThat(snap).isNull();
    }

    @Test
    void should_computeDailyPnl_whenPriorDerivativeSnapshotExists() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("35.20"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .portfolioId(1L)
                .assetType(AssetType.VIOP)
                .assetCode("F_USDTRY0626")
                .quantity(new BigDecimal("1"))
                .unitPriceTry(new BigDecimal("35.20"))
                .marketValueTry(new BigDecimal("35200"))
                .build();
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(AssetType.VIOP),
                        org.mockito.ArgumentMatchers.eq("F_USDTRY0626"),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(prior));

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos,
                LocalDateTime.of(2026, 5, 2, 18, 0));

        assertThat(snap.getDailyPnlTry()).isNotNull();
        assertThat(snap.getDailyPnlPercent()).isNotNull();
    }

    @Test
    void should_buildAggregatedAssetSnapshot_when_trackedAssetIsProvided() {
        com.finance.common.model.TrackedAsset tracked = com.finance.common.model.TrackedAsset.builder()
                .id(42L)
                .assetType(com.finance.common.model.TrackedAssetType.STOCK)
                .assetCode("THYAO.IS")
                .build();
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());

        PortfolioAssetDailySnapshot snap = service.buildAggregatedAssetSnapshot(
                1L, AssetType.STOCK, "THYAO.IS", tracked,
                LocalDateTime.of(2026, 5, 1, 18, 0),
                new BigDecimal("100"), new BigDecimal("4000.0000"), new BigDecimal("50.0000"));

        assertThat(snap.getMarketValueTry()).isEqualByComparingTo("5000.0000");
        assertThat(snap.getTotalCostTry()).isEqualByComparingTo("4000.0000");
        assertThat(snap.getPnlTry()).isEqualByComparingTo("1000.0000");
    }

    @Test
    void should_buildAggregatedAssetSnapshotWithPrior_when_priorIsExplicit() {
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .quantity(new BigDecimal("100"))
                .unitPriceTry(new BigDecimal("45.0000"))
                .build();

        PortfolioAssetDailySnapshot snap = service.buildAggregatedAssetSnapshotWithPrior(
                1L, AssetType.STOCK, "THYAO.IS", null,
                LocalDateTime.of(2026, 5, 1, 18, 0),
                new BigDecimal("100"), new BigDecimal("4000.0000"), new BigDecimal("50.0000"),
                prior);

        assertThat(snap.getDailyPnlTry()).isEqualByComparingTo("500.0000");
        assertThat(snap.getDailyPnlPercent()).isNotNull();
    }

    @Test
    void should_returnNullDailyDelta_when_priorAssetSnapshotMissingPriceOrQuantity() {
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .quantity(null)
                .unitPriceTry(new BigDecimal("45.0000"))
                .build();

        PortfolioAssetDailySnapshot snap = service.buildAggregatedAssetSnapshotWithPrior(
                1L, AssetType.STOCK, "THYAO.IS", null,
                LocalDateTime.of(2026, 5, 1, 18, 0),
                new BigDecimal("100"), new BigDecimal("4000.0000"), new BigDecimal("50.0000"),
                prior);

        assertThat(snap.getDailyPnlTry()).isNull();
        assertThat(snap.getDailyPnlPercent()).isNull();
    }

    @Test
    void should_useExplicitRowsForDailyDelta_when_buildingAggregateFromRows() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        PortfolioAssetDailySnapshot row1 = PortfolioAssetDailySnapshot.builder()
                .dailyPnlTry(new BigDecimal("100.0000"))
                .marketValueTry(new BigDecimal("1100.0000"))
                .build();
        PortfolioAssetDailySnapshot row2 = PortfolioAssetDailySnapshot.builder()
                .dailyPnlTry(new BigDecimal("50.0000"))
                .marketValueTry(new BigDecimal("550.0000"))
                .build();
        PortfolioPosition pos = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("10.0000"));
        java.util.Map<com.finance.shared.service.AssetPricingPort.AssetKey, BigDecimal> prices =
                java.util.Map.of(pos.toAssetKey(), new BigDecimal("11.0000"));

        PortfolioDailySnapshot snap = service.buildAggregateSnapshotAtFromRows(portfolio,
                LocalDateTime.of(2026, 5, 1, 18, 0),
                List.of(pos), List.of(), prices, List.of(row1, row2));

        assertThat(snap.getDailyPnlTry()).isEqualByComparingTo("150.0000");
    }

    @Test
    void should_handleNullRowsList_when_buildingAggregateFromRows() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        PortfolioPosition pos = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("10.0000"));
        java.util.Map<com.finance.shared.service.AssetPricingPort.AssetKey, BigDecimal> prices =
                java.util.Map.of(pos.toAssetKey(), new BigDecimal("11.0000"));

        PortfolioDailySnapshot snap = service.buildAggregateSnapshotAtFromRows(portfolio,
                LocalDateTime.now(),
                List.of(pos), List.of(), prices, null);

        assertThat(snap.getDailyPnlTry()).isNull();
    }

    @Test
    void should_includeAggregateDailyContributions_when_buildAggregateSnapshotFindsHeldRows() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        PortfolioPosition pos = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40.0000"));
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of(pos));
        when(pricingPort.getPriceTry(MarketType.STOCK, "THYAO.IS"))
                .thenReturn(new BigDecimal("50.0000"));

        PortfolioAssetDailySnapshot held = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.STOCK)
                .assetCode("THYAO.IS")
                .dailyPnlTry(new BigDecimal("100.0000"))
                .marketValueTry(new BigDecimal("5000.0000"))
                .build();
        org.mockito.Mockito.reset(assetSnapshotRepository);
        when(assetSnapshotRepository.findLatestPerAsset(1L)).thenReturn(List.of(held));

        PortfolioDailySnapshot snap = service.buildAggregateSnapshot(portfolio,
                LocalDateTime.of(2026, 5, 1, 18, 0));

        assertThat(snap.getDailyPnlTry()).isEqualByComparingTo("100.0000");
    }

    @Test
    void should_filterOutUnheldRows_when_aggregateBuildScansLatestPerAsset() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        PortfolioPosition pos = stubPosition(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40.0000"));
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of(pos));
        when(pricingPort.getPriceTry(MarketType.STOCK, "THYAO.IS"))
                .thenReturn(new BigDecimal("50.0000"));

        PortfolioAssetDailySnapshot foreign = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.CRYPTO)
                .assetCode("bitcoin")
                .dailyPnlTry(new BigDecimal("999.0000"))
                .marketValueTry(new BigDecimal("5000.0000"))
                .build();
        org.mockito.Mockito.reset(assetSnapshotRepository);
        when(assetSnapshotRepository.findLatestPerAsset(1L)).thenReturn(List.of(foreign));

        PortfolioDailySnapshot snap = service.buildAggregateSnapshot(portfolio,
                LocalDateTime.of(2026, 5, 1, 18, 0));

        assertThat(snap.getDailyPnlTry()).isNull();
    }

    @Test
    void should_returnNullDailyPnl_when_priorSnapshotMissing() {
        com.finance.common.model.TrackedAsset tracked = com.finance.common.model.TrackedAsset.builder()
                .id(42L)
                .assetType(com.finance.common.model.TrackedAssetType.STOCK)
                .assetCode("THYAO.IS")
                .build();
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());

        PortfolioAssetDailySnapshot snap = service.buildAggregatedAssetSnapshot(
                1L, AssetType.STOCK, "THYAO.IS", tracked,
                LocalDateTime.of(2026, 5, 1, 18, 0),
                new BigDecimal("100"), new BigDecimal("4000.0000"), new BigDecimal("50.0000"));

        assertThat(snap.getDailyPnlTry()).isNull();
    }

    @Test
    void should_useOlderAsPrior_when_olderSnapshotExists() {
        com.finance.common.model.TrackedAsset tracked = com.finance.common.model.TrackedAsset.builder()
                .id(42L)
                .assetType(com.finance.common.model.TrackedAssetType.STOCK)
                .assetCode("THYAO.IS")
                .build();
        LocalDateTime target = LocalDateTime.of(2026, 5, 1, 18, 0);
        PortfolioAssetDailySnapshot older = PortfolioAssetDailySnapshot.builder()
                .quantity(new BigDecimal("100"))
                .unitPriceTry(new BigDecimal("45.0000"))
                .createdAt(target.minusHours(24).minusHours(1))
                .build();
        when(assetSnapshotRepository
                .findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(older));

        PortfolioAssetDailySnapshot snap = service.buildAggregatedAssetSnapshot(
                1L, AssetType.STOCK, "THYAO.IS", tracked, target,
                new BigDecimal("100"), new BigDecimal("4000.0000"), new BigDecimal("50.0000"));

        assertThat(snap.getDailyPnlTry()).isNotNull();
    }
}
