package com.finance.portfolio.service;

import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;

import com.finance.portfolio.dto.internal.PortfolioAggregateRow;
import com.finance.portfolio.dto.response.AssetSeriesPoint;
import com.finance.portfolio.dto.response.PerformanceAssetDetail;
import com.finance.portfolio.dto.response.PerformanceEvent;
import com.finance.portfolio.dto.response.PerformancePoint;
import com.finance.portfolio.mapper.PortfolioSnapshotMapper;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PerformanceEventType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioPerformanceServiceTest {

    private static final Long PORTFOLIO_ID = 1L;

    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private PortfolioDailySnapshotRepository dailySnapshotRepository;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private com.finance.portfolio.derivative.repository.DerivativePositionRepository derivativePositionRepository;
    @Mock private TrackedAssetRepository trackedAssetRepository;
    @Mock private PortfolioSnapshotMapper snapshotMapper;

    private PortfolioPerformanceService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioPerformanceService(
                assetSnapshotRepository, dailySnapshotRepository, positionRepository,
                derivativePositionRepository,
                trackedAssetRepository, snapshotMapper,
                new PerformanceEventAssembler(),
                new PerformanceAggregationHelper(new com.finance.portfolio.config.PortfolioProperties()));
        org.mockito.Mockito.lenient().when(derivativePositionRepository.findOpenByPortfolio(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(java.util.List.of());
    }

    @Test
    void shouldReturnAggregatePerformancePointsWithDetails_whenNoAssetTypeFilter() {
        LocalDateTime t = LocalDateTime.of(2026, 4, 10, 23, 0);
        PortfolioAggregateRow agg = aggregate(t,
                new BigDecimal("3000"), new BigDecimal("2900"), new BigDecimal("100"));
        PortfolioAssetDailySnapshot stockSnap = assetSnap(t, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("2000"), new BigDecimal("80"));
        PortfolioAssetDailySnapshot cryptoSnap = assetSnap(t, AssetType.CRYPTO, "bitcoin",
                new BigDecimal("1000"), new BigDecimal("20"));
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(stockSnap, cryptoSnap));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        assertThat(result).hasSize(1);
        PerformancePoint point = result.get(0);
        assertThat(point.totalValueTry()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(point.details()).hasSize(2);
        assertThat(point.details().get(0).valueTry()).isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    void shouldEmitPositionAddedEvents_whenLotsAddedInWindow() {
        LocalDateTime now = LocalDateTime.now();
        PortfolioAggregateRow agg = aggregate(now,
                new BigDecimal("4000"), new BigDecimal("4000"), BigDecimal.ZERO);
        PortfolioPosition newLot = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), now.minusDays(1));
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of());
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(newLot));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        List<PerformanceEvent> events = result.get(0).events();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(PerformanceEventType.POSITION_ADDED);
        assertThat(events.get(0).valueTry()).isEqualByComparingTo(new BigDecimal("4000.0000"));
    }

    @Test
    void shouldNotEmitMarketDriftEvents_whenNoTradeOccurredBetweenSnapshots() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 10, 23, 0);
        LocalDateTime t2 = t1.plusDays(1);
        PortfolioAggregateRow a1 = aggregate(t1,
                new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO);
        PortfolioAggregateRow a2 = aggregate(t2,
                new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("20"));
        PortfolioAssetDailySnapshot snap1 = assetSnap(t1, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), BigDecimal.ZERO);
        PortfolioAssetDailySnapshot snap2 = assetSnap(t2, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("120"), new BigDecimal("20"));
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(a1, a2));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(snap1, snap2));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        assertThat(result.get(1).events()).isEmpty();
    }

    @Test
    void shouldNotOvercountAcrossMultipleBatchesPerDay_whenAggregateChartReadsAssets() {
        LocalDateTime t1 = LocalDateTime.of(2026, 5, 19, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 5, 19, 14, 0);
        PortfolioAggregateRow agg = aggregate(t2,
                new BigDecimal("48000"), new BigDecimal("48000"), BigDecimal.ZERO);
        PortfolioAssetDailySnapshot batch1Eth = assetSnap(t1, AssetType.CRYPTO, "ethereum",
                new BigDecimal("48000"), BigDecimal.ZERO);
        PortfolioAssetDailySnapshot batch2Eth = assetSnap(t2, AssetType.CRYPTO, "ethereum",
                new BigDecimal("48000"), BigDecimal.ZERO);
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(batch1Eth, batch2Eth));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        assertThat(result).hasSize(1);
        PerformanceAssetDetail cryptoDetail = result.get(0).details().stream()
                .filter(d -> "CRYPTO".equals(d.label())).findFirst().orElseThrow();
        assertThat(cryptoDetail.valueTry()).isEqualByComparingTo(new BigDecimal("48000"));
    }

    @Test
    void shouldComputeAssetTypePerformanceFromAssetSnapshotsOnly_whenFilterProvided() {
        LocalDateTime t = LocalDateTime.of(2026, 4, 10, 23, 0);
        PortfolioAssetDailySnapshot stockA = assetSnapWithCost(t, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("6000"), new BigDecimal("4000"), new BigDecimal("2000"));
        PortfolioAssetDailySnapshot stockB = assetSnapWithCost(t, AssetType.STOCK, "ASELS.IS",
                new BigDecimal("3000"), new BigDecimal("2000"), new BigDecimal("1000"));
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.STOCK), any(), any()))
                .thenReturn(List.of(stockA, stockB));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "STOCK");

        assertThat(result).hasSize(1);
        PerformancePoint point = result.get(0);
        assertThat(point.totalValueTry()).isEqualByComparingTo(new BigDecimal("9000"));
        assertThat(point.totalPnlTry()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(point.details()).extracting("label")
                .containsExactly("THYAO.IS", "ASELS.IS");
    }

    @Test
    void shouldSumPerLotSnapshots_whenMultipleSnapshotsShareSameAssetCodeAtSameTimestamp() {
        LocalDateTime t = LocalDateTime.of(2026, 5, 19, 23, 0);
        PortfolioAssetDailySnapshot lotASnap = assetSnapWithCost(t, AssetType.FUND, "BND",
                new BigDecimal("309"), new BigDecimal("300"), new BigDecimal("9"));
        PortfolioAssetDailySnapshot lotBSnap = assetSnapWithCost(t, AssetType.FUND, "BND",
                new BigDecimal("154.5"), new BigDecimal("150"), new BigDecimal("4.5"));
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.FUND), any(), any()))
                .thenReturn(List.of(lotASnap, lotBSnap));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "FUND");

        assertThat(result).hasSize(1);
        PerformancePoint point = result.get(0);
        assertThat(point.totalValueTry()).isEqualByComparingTo(new BigDecimal("463.5"));
        assertThat(point.totalPnlTry()).isEqualByComparingTo(new BigDecimal("13.5"));
        assertThat(point.details()).hasSize(1);
        assertThat(point.details().get(0).valueTry()).isEqualByComparingTo(new BigDecimal("463.5"));
    }

    @Test
    void shouldDelegateToAssetSnapshotMapper_whenFetchingAssetSeries() {
        PortfolioAssetDailySnapshot snap = assetSnap(
                LocalDateTime.now(), AssetType.CRYPTO, "bitcoin",
                new BigDecimal("2500000"), new BigDecimal("100000"));
        AssetSeriesPoint expected = new AssetSeriesPoint(LocalDateTime.now(),
                new BigDecimal("2500000"), new BigDecimal("2500000"), new BigDecimal("100000"),
                null, null, List.of());
        TrackedAsset tracked = TrackedAsset.builder().id(42L).assetType(TrackedAssetType.CRYPTO).assetCode("bitcoin").build();
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(java.util.Optional.of(tracked));
        when(assetSnapshotRepository.findByPortfolioIdAndTrackedAssetIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(42L), any(), any()))
                .thenReturn(List.of(snap));
        when(snapshotMapper.toAssetSeriesPoints(List.of(snap))).thenReturn(List.of(expected));

        List<AssetSeriesPoint> result = service.getAssetSeries(PORTFOLIO_ID, "CRYPTO", "bitcoin", "1M");

        assertThat(result).containsExactly(expected);
    }

    @Test
    void shouldAttachEntryAndExitEvents_whenAssetLotsOpenedAndClosedWithinRange() {
        LocalDateTime snapTs = LocalDateTime.now();
        PortfolioAssetDailySnapshot snap = assetSnap(
                snapTs, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("4000"), new BigDecimal("200"));
        AssetSeriesPoint mapped = new AssetSeriesPoint(snapTs,
                new BigDecimal("40"), new BigDecimal("4000"), new BigDecimal("200"),
                null, null, List.of());
        TrackedAsset tracked = TrackedAsset.builder().id(7L).assetType(TrackedAssetType.STOCK).assetCode("THYAO.IS").build();
        PortfolioPosition openLot = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), snapTs.minusHours(2));
        PortfolioPosition closedLot = closedLot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("50"), new BigDecimal("38"), new BigDecimal("42"),
                snapTs.minusDays(3), snapTs.minusHours(1));
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.STOCK, "THYAO.IS"))
                .thenReturn(java.util.Optional.of(tracked));
        when(assetSnapshotRepository.findByPortfolioIdAndTrackedAssetIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(7L), any(), any()))
                .thenReturn(List.of(snap));
        when(snapshotMapper.toAssetSeriesPoints(List.of(snap))).thenReturn(List.of(mapped));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(openLot, closedLot));

        List<AssetSeriesPoint> result = service.getAssetSeries(PORTFOLIO_ID, "STOCK", "THYAO.IS", "1M");

        assertThat(result).hasSize(1);
        List<PerformanceEvent> events = result.get(0).events();
        assertThat(events).extracting(PerformanceEvent::type)
                .containsExactlyInAnyOrder(PerformanceEventType.POSITION_ADDED, PerformanceEventType.POSITION_ADDED,
                        PerformanceEventType.POSITION_SOLD);
        assertThat(events).extracting(PerformanceEvent::assetCode)
                .allMatch(code -> "THYAO.IS".equals(code));
    }

    private PortfolioAggregateRow aggregate(LocalDateTime ts, BigDecimal totalValue,
                                            BigDecimal totalCost, BigDecimal totalPnl) {
        return new PortfolioAggregateRow(ts, totalValue, BigDecimal.ZERO, totalCost, totalPnl);
    }

    private PortfolioAssetDailySnapshot assetSnap(LocalDateTime ts, AssetType type, String code,
                                                   BigDecimal marketValue, BigDecimal pnl) {
        return assetSnapWithCost(ts, type, code, marketValue, marketValue.subtract(pnl), pnl);
    }

    private PortfolioAssetDailySnapshot assetSnapWithCost(LocalDateTime ts, AssetType type, String code,
                                                           BigDecimal marketValue, BigDecimal totalCost,
                                                           BigDecimal pnl) {
        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID)
                .assetType(type).assetCode(code)
                .quantity(BigDecimal.ONE)
                .unitPriceTry(marketValue)
                .marketValueTry(marketValue)
                .totalCostTry(totalCost)
                .pnlTry(pnl)
                .snapshotDate(ts.toLocalDate())
                .createdAt(ts)
                .build();
    }

    private PortfolioPosition lot(AssetType type, String code, BigDecimal qty,
                                   BigDecimal entryPrice, LocalDateTime createdAt) {
        return PortfolioPosition.builder()
                .assetType(type).assetCode(code)
                .quantity(qty)
                .entryDate(createdAt)
                .entryPrice(entryPrice)
                .createdAt(createdAt)
                .build();
    }

    private PortfolioPosition closedLot(AssetType type, String code, BigDecimal qty,
                                         BigDecimal entryPrice, BigDecimal exitPrice,
                                         LocalDateTime entryDate, LocalDateTime exitDate) {
        return PortfolioPosition.builder()
                .assetType(type).assetCode(code)
                .quantity(qty)
                .entryDate(entryDate)
                .entryPrice(entryPrice)
                .exitDate(exitDate)
                .exitPrice(exitPrice)
                .createdAt(entryDate)
                .build();
    }

    @Test
    void shouldReturnCumulativeRealizedSeries_whenAssetTypeIsCash() {
        LocalDateTime t1 = LocalDateTime.now().withSecond(0).withNano(0).minusDays(2);
        LocalDateTime t2 = t1.plusDays(1);
        PortfolioAggregateRow agg1 = new PortfolioAggregateRow(t1, BigDecimal.ZERO,
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
        PortfolioAggregateRow agg2 = new PortfolioAggregateRow(t2, BigDecimal.ZERO,
                new BigDecimal("250"), BigDecimal.ZERO, BigDecimal.ZERO);
        PortfolioPosition closed = closedLot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("110"),
                t1.minusDays(2), t1.minusHours(1));
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg1, agg2));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "cash");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).totalValueTry()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(result.get(0).pnlPercent()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.get(1).totalValueTry()).isEqualByComparingTo(new BigDecimal("250"));
    }

    @Test
    void shouldReturnZeroPercent_whenNoClosedPositionsForCashPath() {
        LocalDateTime t = LocalDateTime.now().withSecond(0).withNano(0).minusDays(1);
        PortfolioAggregateRow agg = new PortfolioAggregateRow(t, BigDecimal.ZERO, null,
                BigDecimal.ZERO, BigDecimal.ZERO);
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "CASH");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.get(0).pnlPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldEmitCloseEvents_whenSpotAndDerivativeClosedInsideCashWindow() {
        LocalDateTime t = LocalDateTime.now().withSecond(0).withNano(0);
        PortfolioAggregateRow agg = new PortfolioAggregateRow(t, BigDecimal.ZERO,
                new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO);
        PortfolioPosition spotClosed = closedLot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("120"),
                t.minusDays(5), t.minusMinutes(30));
        ViopContract contract = contract("XU030F", "10", "TRY");
        DerivativePosition closedDeriv = closedDerivative(contract, DerivativeDirection.LONG,
                "100", "1", "110",
                LocalDate.now().minusDays(5), LocalDate.now());
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(spotClosed));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closedDeriv));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "CASH");

        assertThat(result).hasSize(1);
        List<PerformanceEvent> events = result.get(0).events();
        assertThat(events).extracting(PerformanceEvent::type)
                .containsOnly(PerformanceEventType.POSITION_SOLD);
        assertThat(events).extracting(PerformanceEvent::assetCode)
                .containsExactlyInAnyOrder("THYAO.IS", "XU030F");
    }

    @Test
    void shouldReturnEmpty_whenAssetSeriesRequestedForUnknownAsset() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(any(), any()))
                .thenReturn(java.util.Optional.empty());

        List<AssetSeriesPoint> result = service.getAssetSeries(PORTFOLIO_ID, "STOCK", "UNKNOWN", "1M");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnViopAssetSeries_whenAssetTypeIsViop() {
        LocalDateTime t = LocalDateTime.now().withSecond(0).withNano(0);
        PortfolioAssetDailySnapshot snap = assetSnap(t, AssetType.VIOP, "XU030F",
                new BigDecimal("1500"), new BigDecimal("50"));
        AssetSeriesPoint mapped = new AssetSeriesPoint(t,
                new BigDecimal("15"), new BigDecimal("1500"), new BigDecimal("50"),
                null, null, List.of());
        ViopContract contract = contract("XU030F", "10", "TRY");
        DerivativePosition openDeriv = openDerivative(contract, DerivativeDirection.LONG, "100", "1",
                LocalDate.now().minusDays(2));
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.VIOP), eq("XU030F"), any(), any()))
                .thenReturn(List.of(snap));
        when(snapshotMapper.toAssetSeriesPoints(List.of(snap))).thenReturn(List.of(mapped));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID))
                .thenReturn(List.of(openDeriv));

        List<AssetSeriesPoint> result = service.getAssetSeries(PORTFOLIO_ID, "VIOP", "XU030F", "1M");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).events()).extracting(PerformanceEvent::type)
                .containsOnly(PerformanceEventType.POSITION_ADDED);
        assertThat(result.get(0).events().get(0).assetCode()).isEqualTo("XU030F");
    }

    @Test
    void shouldComputeViopAssetTypePerformance_whenFilterIsViop() {
        LocalDateTime t = LocalDateTime.now().withSecond(0).withNano(0).minusDays(1);
        PortfolioAssetDailySnapshot snap = assetSnapWithCost(t, AssetType.VIOP, "XU030F",
                new BigDecimal("1100"), new BigDecimal("1000"), new BigDecimal("100"));
        ViopContract contract = contract("XU030F", "10", "TRY");
        DerivativePosition addedDeriv = openDerivative(contract, DerivativeDirection.LONG, "100", "1",
                t.toLocalDate().minusDays(2));
        DerivativePosition closedDeriv = closedDerivative(contract, DerivativeDirection.SHORT, "120", "1", "110",
                t.toLocalDate().minusDays(5), t.toLocalDate().minusDays(3));
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.VIOP), any(), any()))
                .thenReturn(List.of(snap));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID))
                .thenReturn(List.of(addedDeriv, closedDeriv));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "VIOP");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalValueTry()).isEqualByComparingTo(new BigDecimal("1100"));
    }

    @Test
    void shouldCapDetailsWithOtherBucket_whenAssetCountExceedsTopN() {
        LocalDateTime t = LocalDateTime.of(2026, 5, 19, 12, 0);
        List<PortfolioAssetDailySnapshot> snaps = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            BigDecimal value = new BigDecimal((12 - i) * 100);
            snaps.add(assetSnapWithCost(t, AssetType.STOCK, "SYM" + i, value, value, BigDecimal.ZERO));
        }
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.STOCK), any(), any()))
                .thenReturn(snaps);

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "STOCK");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).details()).hasSize(8);
        PerformanceAssetDetail last = result.get(0).details().get(7);
        assertThat(last.label()).isEqualTo("OTHER");
        assertThat(last.valueTry()).isPositive();
    }

    @Test
    void shouldKeepAnonymousSnapshots_whenAssetCodeIsNullForAssetTypePath() {
        LocalDateTime t = LocalDateTime.now().withSecond(0).withNano(0);
        PortfolioAssetDailySnapshot anonymous = PortfolioAssetDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID)
                .assetType(AssetType.STOCK)
                .quantity(BigDecimal.ONE)
                .unitPriceTry(new BigDecimal("10"))
                .marketValueTry(new BigDecimal("10"))
                .totalCostTry(new BigDecimal("10"))
                .pnlTry(BigDecimal.ZERO)
                .snapshotDate(t.toLocalDate())
                .createdAt(t)
                .build();
        PortfolioAssetDailySnapshot normal = assetSnapWithCost(t, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("200"), new BigDecimal("200"), BigDecimal.ZERO);
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.STOCK), any(), any()))
                .thenReturn(List.of(anonymous, normal));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "STOCK");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).details()).hasSize(2);
    }

    @Test
    void shouldEmitDerivativeAddedAndClosedEvents_whenAssetTypeIsViop() {
        LocalDateTime t = LocalDateTime.now().withSecond(0).withNano(0);
        PortfolioAssetDailySnapshot snap = assetSnapWithCost(t, AssetType.VIOP, "XU030F",
                new BigDecimal("1100"), new BigDecimal("1000"), new BigDecimal("100"));
        ViopContract contract = contract("XU030F", "10", "TRY");
        LocalDate entryDate = t.toLocalDate().minusDays(2);
        LocalDate closeDate = t.toLocalDate().minusDays(1);
        DerivativePosition added = openDerivative(contract, DerivativeDirection.LONG, "100", "1", entryDate);
        DerivativePosition closed = closedDerivative(contract, DerivativeDirection.LONG,
                "100", "1", "110", entryDate, closeDate);
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.VIOP), any(), any()))
                .thenReturn(List.of(snap));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID))
                .thenReturn(List.of(added, closed));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "VIOP");

        assertThat(result).hasSize(1);
        List<PerformanceEvent> events = result.get(0).events();
        assertThat(events).extracting(PerformanceEvent::type)
                .contains(PerformanceEventType.POSITION_ADDED, PerformanceEventType.POSITION_SOLD);
        assertThat(events).extracting(PerformanceEvent::assetCode)
                .allMatch("XU030F"::equals);
    }

    @Test
    void shouldUseZeroProceeds_whenSoldLotHasNoExitPrice() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        PortfolioAggregateRow agg = aggregate(now,
                new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO);
        PortfolioPosition closedWithoutExitPrice = PortfolioPosition.builder()
                .assetType(AssetType.STOCK).assetCode("THYAO.IS")
                .quantity(new BigDecimal("10"))
                .entryDate(now.minusYears(2))
                .entryPrice(new BigDecimal("40"))
                .exitDate(now.minusMinutes(15))
                .createdAt(now.minusYears(2))
                .build();
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of());
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closedWithoutExitPrice));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        List<PerformanceEvent> events = result.get(0).events();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(PerformanceEventType.POSITION_SOLD);
        assertThat(events.get(0).valueTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private ViopContract contract(String symbol, String size, String currency) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .contractSize(size != null ? new BigDecimal(size) : null)
                .currency(currency)
                .active(true)
                .build();
    }

    private DerivativePosition openDerivative(ViopContract c, DerivativeDirection dir,
                                               String entry, String qty, LocalDate entryDate) {
        return DerivativePosition.builder()
                .viopContract(c)
                .direction(dir)
                .entryDate(entryDate)
                .entryPrice(new BigDecimal(entry))
                .quantityLot(new BigDecimal(qty))
                .build();
    }

    private DerivativePosition closedDerivative(ViopContract c, DerivativeDirection dir,
                                                  String entry, String qty, String close,
                                                  LocalDate entryDate, LocalDate closeDate) {
        DerivativePosition dp = openDerivative(c, dir, entry, qty, entryDate);
        dp.setCloseDate(closeDate);
        dp.setClosePrice(new BigDecimal(close));
        return dp;
    }
}
