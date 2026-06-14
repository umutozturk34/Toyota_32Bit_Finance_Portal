package com.finance.portfolio.service.performance;

import com.finance.portfolio.service.pricing.MultiCurrencyPnlCalculator;

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
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
    @Mock private com.finance.market.core.service.HistoricalPricingPort historicalPricingPort;

    private PortfolioPerformanceService service;

    @BeforeEach
    void setUp() {
        PerformanceEventAssembler eventAssembler = new PerformanceEventAssembler();
        PerCurrencyFrameCalculator frameCalculator =
                new PerCurrencyFrameCalculator(new MultiCurrencyPnlCalculator(historicalPricingPort));
        PerformanceEntryFootprintBuilder footprintBuilder = new PerformanceEntryFootprintBuilder();
        ViopDirectionSeriesRecomputer viopDirectionRecomputer = new ViopDirectionSeriesRecomputer();
        PerformanceAggregationHelper aggregationHelper =
                new PerformanceAggregationHelper(new com.finance.portfolio.config.PortfolioProperties());
        CashRealisedSeriesBuilder cashSeriesBuilder = new CashRealisedSeriesBuilder(
                dailySnapshotRepository, positionRepository, derivativePositionRepository,
                footprintBuilder, frameCalculator, eventAssembler);
        AssetSeriesBuilder assetSeriesBuilder = new AssetSeriesBuilder(
                assetSnapshotRepository, positionRepository, derivativePositionRepository,
                trackedAssetRepository, snapshotMapper, viopDirectionRecomputer, eventAssembler);
        AggregatePerformanceBuilder aggregatePerformanceBuilder = new AggregatePerformanceBuilder(
                dailySnapshotRepository, assetSnapshotRepository, positionRepository, derivativePositionRepository,
                footprintBuilder, frameCalculator, aggregationHelper, eventAssembler, cashSeriesBuilder);
        service = new PortfolioPerformanceService(
                assetSnapshotRepository, positionRepository, derivativePositionRepository,
                frameCalculator, footprintBuilder, cashSeriesBuilder, assetSeriesBuilder, aggregatePerformanceBuilder);
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
    void shouldExcludeFullyClosedSpotFromAllViewTypeContributor() {
        LocalDateTime today = LocalDateTime.now().withSecond(0).withNano(0);
        PortfolioAggregateRow agg = aggregate(today,
                new BigDecimal("1000"), new BigDecimal("900"), new BigDecimal("100"));
        PortfolioAssetDailySnapshot akbnk = assetSnapWithCost(today, AssetType.STOCK, "AKBNK",
                new BigDecimal("1000"), new BigDecimal("900"), new BigDecimal("100"));
        // TUPRS fully sold TODAY: its close-day row lingers in the assets for this timestamp.
        PortfolioAssetDailySnapshot tuprsClose = assetSnapWithCost(today, AssetType.STOCK, "TUPRS",
                new BigDecimal("500"), new BigDecimal("450"), new BigDecimal("50"));
        PortfolioPosition akbnkOpen = lot(AssetType.STOCK, "AKBNK",
                new BigDecimal("10"), new BigDecimal("90"), today.minusDays(5));
        PortfolioPosition tuprsSold = closedLot(AssetType.STOCK, "TUPRS",
                new BigDecimal("10"), new BigDecimal("45"), new BigDecimal("50"), today.minusDays(5), today);
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(akbnk, tuprsClose));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(akbnkOpen, tuprsSold));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        assertThat(result).hasSize(1);
        PerformanceAssetDetail stock = result.get(0).details().stream()
                .filter(d -> "STOCK".equals(d.label())).findFirst().orElseThrow();
        // STOCK contributor = open AKBNK (1000) only; the sold-today TUPRS close-day row (500) is dropped, so it no
        // longer shows as a live contributor beside the closed bucket under the "Tümü" view.
        assertThat(stock.valueTry()).isEqualByComparingTo(new BigDecimal("1000"));
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
                new BigDecimal("2500000"), new BigDecimal("2500000"), new BigDecimal("2400000"), new BigDecimal("100000"),
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
                new BigDecimal("40"), new BigDecimal("4000"), new BigDecimal("3800"), new BigDecimal("200"),
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

    @Test
    void shouldMergeSameTimestampViopLots_whenHedgeOnSameSymbol() {
        LocalDateTime t = LocalDateTime.now().withSecond(0).withNano(0);
        // 1 LONG + 1 SHORT lot on the SAME symbol at the SAME timestamp persist as two per-lot rows; the per-asset
        // series must collapse them into ONE direction-aware point (else the chart halves: $value/2).
        PortfolioAssetDailySnapshot longLeg = assetSnap(t, AssetType.VIOP, "XU030F", new BigDecimal("1500"), new BigDecimal("-50"));
        PortfolioAssetDailySnapshot shortLeg = assetSnap(t, AssetType.VIOP, "XU030F", new BigDecimal("1500"), new BigDecimal("50"));
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.VIOP), eq("XU030F"), any(), any()))
                .thenReturn(List.of(longLeg, shortLeg));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PortfolioAssetDailySnapshot>> captor = ArgumentCaptor.forClass(List.class);
        when(snapshotMapper.toAssetSeriesPoints(captor.capture())).thenReturn(List.of());

        service.getAssetSeries(PORTFOLIO_ID, "VIOP", "XU030F", "1M");

        List<PortfolioAssetDailySnapshot> merged = captor.getValue();
        assertThat(merged).hasSize(1);                                              // two lots collapsed to one
        assertThat(merged.get(0).getMarketValueTry()).isEqualByComparingTo("3000"); // 1500 + 1500
        assertThat(merged.get(0).getTotalCostTry()).isEqualByComparingTo("3000");   // 1550 + 1450
        assertThat(merged.get(0).getPnlTry()).isEqualByComparingTo("0");            // -50 + 50 nets (hedge)
    }

    @Test
    void dailyReturnIndexByCcy_returnsCostBasedReturnIndex_perCurrency() {
        // Arrange — a 2000 TRY-cost STOCK lot (entered 2024-06-01) worth 2200 TRY on 2024-06-04 (+10%).
        // USD/TRY flat at 40, so the FX cancels and the USD return index must equal the TRY return index (110):
        // the line plots the real cost-based return, not a single-date conversion of the netted TRY index.
        LocalDate entry = LocalDate.of(2024, 6, 1);
        LocalDate point = LocalDate.of(2024, 6, 4);
        java.util.Map<LocalDate, BigDecimal> flatFx = new java.util.HashMap<>();
        flatFx.put(entry, new BigDecimal("40"));
        flatFx.put(point, new BigDecimal("40"));
        org.mockito.Mockito.lenient().when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(flatFx);
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(
                lot(AssetType.STOCK, "THYAO.IS", new BigDecimal("10"), new BigDecimal("200"), entry.atStartOfDay())));
        when(assetSnapshotRepository.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of());
        PortfolioDailySnapshot snap = PortfolioDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID).snapshotDate(point)
                .totalValueTry(new BigDecimal("2200")).totalPnlTry(new BigDecimal("200")).build();

        java.util.Map<LocalDate, java.util.Map<String, BigDecimal>> index =
                service.dailyReturnIndexByCcy(PORTFOLIO_ID, List.of(snap));

        // 100 + 100 × (2200 − 2000)/2000 = 110, identical in USD because the flat FX cancels.
        assertThat(index.get(point).get("USD")).isEqualByComparingTo(new BigDecimal("110"));
    }

    @Test
    void shouldReconstituteBlendedSeries_whenSplitViopByDirection() {
        // Snapshots are direction-blind, so a per-direction chart is RECOMPUTED from the shared unit price + only
        // that direction's lots. Hedge on ONE size-1 TRY contract: LONG entry 100, SHORT entry 100, unit 120 →
        // LONG +20 (profiting long), SHORT −20 (losing short, NOT the direction-blind +20). The two legs must
        // reconstitute the blended hedge exactly (value 240, cost 200, pnl 0) — the recompute's correctness anchor.
        LocalDateTime t = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDate entryDate = t.toLocalDate().minusDays(5);
        PortfolioAssetDailySnapshot longLeg = assetSnapWithCost(t, AssetType.VIOP, "XU030F",
                new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("20"));
        PortfolioAssetDailySnapshot shortLeg = assetSnapWithCost(t, AssetType.VIOP, "XU030F",
                new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("-20"));
        ViopContract c = contract("XU030F", "1", "TRY");
        DerivativePosition longPos = openDerivative(c, DerivativeDirection.LONG, "100", "1", entryDate);
        DerivativePosition shortPos = openDerivative(c, DerivativeDirection.SHORT, "100", "1", entryDate);
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.VIOP), eq("XU030F"), any(), any()))
                .thenReturn(List.of(longLeg, shortLeg));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(longPos, shortPos));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PortfolioAssetDailySnapshot>> captor = ArgumentCaptor.forClass(List.class);
        when(snapshotMapper.toAssetSeriesPoints(captor.capture())).thenReturn(List.of());

        service.getAssetSeries(PORTFOLIO_ID, "VIOP", "XU030F", "1M", "LONG");
        service.getAssetSeries(PORTFOLIO_ID, "VIOP", "XU030F", "1M", "SHORT");

        List<List<PortfolioAssetDailySnapshot>> captured = captor.getAllValues();
        PortfolioAssetDailySnapshot longPt = captured.get(0).get(0);
        PortfolioAssetDailySnapshot shortPt = captured.get(1).get(0);
        assertThat(longPt.getPnlTry()).isEqualByComparingTo("20");
        assertThat(longPt.getMarketValueTry()).isEqualByComparingTo("120");
        assertThat(longPt.getTotalCostTry()).isEqualByComparingTo("100");
        assertThat(shortPt.getPnlTry()).isEqualByComparingTo("-20");   // direction-aware, not the blind +20
        // Invariant: LONG + SHORT reconstitutes the blended hedge.
        assertThat(longPt.getMarketValueTry().add(shortPt.getMarketValueTry())).isEqualByComparingTo("240");
        assertThat(longPt.getTotalCostTry().add(shortPt.getTotalCostTry())).isEqualByComparingTo("200");
        assertThat(longPt.getPnlTry().add(shortPt.getPnlTry())).isEqualByComparingTo("0");
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
        // Realized is now computed LIVE per snapDate (direction-aware), not read from the snapshot cash_try.
        // closed: realized (110−100)×10 = 100, settled before t1. closed2: (130−100)×5 = 150, settled on
        // t2's date → cumulative realized 100 at t1, 250 at t2.
        PortfolioPosition closed = closedLot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("110"),
                t1.minusDays(2), t1.minusHours(1));
        PortfolioPosition closed2 = closedLot(AssetType.STOCK, "GARAN.IS",
                new BigDecimal("5"), new BigDecimal("100"), new BigDecimal("130"),
                t1.minusDays(2), t2);
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg1, agg2));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed, closed2));
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
    void shouldNotDoubleCountRealized_whenSpotFullySoldToday_underTypeFilter() {
        LocalDateTime t = LocalDateTime.now().withSecond(0).withNano(0);
        // A stock fully sold TODAY leaves a close-day asset row (marketValue=exit, pnl=realized). That row must NOT
        // feed the OPEN leg: realizedFor already books the realized in the CLOSED leg, so counting it in the open
        // leg too made the TRY Total = openPnl + realized read 2× (the 0.70 → 1.40 doubling under the Stock filter).
        PortfolioAssetDailySnapshot closeDayRow = assetSnapWithCost(t, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("1200"), new BigDecimal("1000"), new BigDecimal("200"));
        PortfolioPosition soldToday = closedLot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("120"),
                t.minusDays(5), t);
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.STOCK), any(), any()))
                .thenReturn(List.of(closeDayRow));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(soldToday));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "STOCK");

        assertThat(result).hasSize(1);
        PerformancePoint point = result.get(0);
        // Open leg is 0 (symbol fully sold) ⇒ Total == realized (cash), booked ONCE. Before the fix Total was 2×.
        assertThat(point.totalPnlTry()).isEqualByComparingTo(point.cashTry());
        // The HELD (open) value line drops to 0 on a full sell — realized lives in the cash/closed leg, not value.
        assertThat(point.totalValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldKeepHistoricalValue_whenStockOpenThenSoldToday_underTypeFilter() {
        LocalDateTime today = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime earlier = today.minusDays(2);
        // A stock held for a week then sold TODAY: a historical row (still open) + the close-day row (exit).
        PortfolioAssetDailySnapshot historical = assetSnapWithCost(earlier, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("1000"), new BigDecimal("900"), new BigDecimal("100"));
        PortfolioAssetDailySnapshot closeDay = assetSnapWithCost(today, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("1200"), new BigDecimal("1000"), new BigDecimal("200"));
        PortfolioPosition soldToday = closedLot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("120"),
                today.minusDays(5), today);
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.STOCK), any(), any()))
                .thenReturn(List.of(historical, closeDay));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(soldToday));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "STOCK");

        assertThat(result).hasSize(2);
        // Date-aware open-leg exclusion: while still open the value line shows the week of history (a later sell must
        // NOT flatten the whole range to 0)...
        assertThat(result.get(0).totalValueTry()).isEqualByComparingTo(new BigDecimal("1000"));
        // ...and the held value only drops to 0 on the close date (the realized moves to the cash/closed leg).
        assertThat(result.get(1).totalValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldExcludeFullyClosedSymbolFromContributorDetails_underTypeFilter() {
        LocalDateTime today = LocalDateTime.now().withSecond(0).withNano(0);
        // Two STOCK symbols: AKBNK still open, TUPRS fully sold TODAY — its close-day row lingers in carriedState.
        PortfolioAssetDailySnapshot akbnk = assetSnapWithCost(today, AssetType.STOCK, "AKBNK",
                new BigDecimal("1000"), new BigDecimal("900"), new BigDecimal("100"));
        PortfolioAssetDailySnapshot tuprs = assetSnapWithCost(today, AssetType.STOCK, "TUPRS",
                new BigDecimal("500"), new BigDecimal("450"), new BigDecimal("50"));
        PortfolioPosition akbnkOpen = lot(AssetType.STOCK, "AKBNK",
                new BigDecimal("10"), new BigDecimal("90"), today.minusDays(5));
        PortfolioPosition tuprsSold = closedLot(AssetType.STOCK, "TUPRS",
                new BigDecimal("10"), new BigDecimal("45"), new BigDecimal("50"), today.minusDays(5), today);
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.STOCK), any(), any()))
                .thenReturn(List.of(akbnk, tuprs));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(akbnkOpen, tuprsSold));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", "STOCK");

        assertThat(result).hasSize(1);
        // AKBNK (open) stays a contributor; TUPRS (fully sold today) drops out — no "+0" ghost beside its
        // realized P&L in the __closed__ bucket.
        assertThat(result.get(0).details()).extracting("label").contains("AKBNK").doesNotContain("TUPRS");
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
                new BigDecimal("15"), new BigDecimal("1500"), new BigDecimal("1450"), new BigDecimal("50"),
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
        PerformancePoint point = result.get(0);
        // VALUE stays the open HELD market value (1100) so the value line drops when sold; but the P&L breakdown
        // FOLDS the closed SHORT derivative's realized +100 (entry 120 -> exit 110, x10 multiplier) so the filtered
        // Kâr/Zarar chart keeps its Total/Open/Closed lines: cashTry = realized 100, total PnL = open 100 + realized 100.
        assertThat(point.totalValueTry()).isEqualByComparingTo("1100");
        assertThat(point.cashTry()).isEqualByComparingTo("100");
        assertThat(point.totalPnlTry()).isEqualByComparingTo("200");
        assertThat(point.openPnlTry()).isEqualByComparingTo("100");
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

    @Test
    void shouldReportOpenShortDerivativeProfitInUsdFrame_whenAggregateNotionalFell() {
        // Arrange: an OPEN SHORT VIOP whose notional FELL (price 100 -> 80) so it PROFITS; USD/TRY drifted up
        // 10->12. A direction-blind value − cost would read the profit as a USD loss; the direction footprint
        // must flip it positive (the pre-fix bug: open SHORT showed as LONG in USD/EUR).
        LocalDate entry = LocalDate.of(2026, 5, 1);
        LocalDateTime t = LocalDateTime.of(2026, 5, 31, 0, 0);
        ViopContract contract = contract("XU030F", "10", "TRY");
        DerivativePosition shortDeriv = openDerivative(contract, DerivativeDirection.SHORT, "100", "1", entry);
        PortfolioAssetDailySnapshot viopSnap = viopAssetSnap(t, "XU030F", new BigDecimal("80"), new BigDecimal("1200"));
        PortfolioAggregateRow agg = aggregate(t, new BigDecimal("1200"), new BigDecimal("1000"), new BigDecimal("200"));
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(viopSnap));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(shortDeriv));
        stubFx(entry, new BigDecimal("10"), t.toLocalDate(), new BigDecimal("12"));

        // Act
        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        // Assert: USD/EUR PnL are POSITIVE (profit), not the negative the raw notional change would give.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).pnlByCcy().get("USD")).isPositive();
        assertThat(result.get(0).pnlByCcy().get("USD")).isCloseTo(new BigDecimal("33.33"), within(new BigDecimal("0.1")));
        assertThat(result.get(0).pnlByCcy().get("EUR")).isPositive();
    }

    @Test
    void shouldCarryDirectionAwarePnlByCcyOnViopDetail_soContributionUsesFrameNotTodayFx() {
        // Regression for the "K/Z Katkısı" VİOP contribution: the per-TYPE detail must carry a per-currency
        // DIRECTION-AWARE pnlByCcy (cost@entry-FX, value@point-FX, SHORT sign flipped) so the breakdown reads it
        // instead of converting the direction-blind TRY notional at TODAY's FX (the wrong huge VİOP figure). Same
        // profiting open SHORT (notional 100→80) as the aggregate test: the VİOP slice's USD PnL must be POSITIVE.
        LocalDate entry = LocalDate.of(2026, 5, 1);
        LocalDateTime t = LocalDateTime.of(2026, 5, 31, 0, 0);
        ViopContract contract = contract("XU030F", "10", "TRY");
        DerivativePosition shortDeriv = openDerivative(contract, DerivativeDirection.SHORT, "100", "1", entry);
        // Consistent snapshot: marketValue = unitPrice(80) × size(10) × lot(1) = 800 (current notional fell from
        // the 1000 entry notional → the SHORT profits). The detail value leg + the per-date footprint notional
        // must agree, exactly as the real assembler writes them.
        PortfolioAssetDailySnapshot viopSnap = viopAssetSnap(t, "XU030F", new BigDecimal("80"), new BigDecimal("800"));
        PortfolioAggregateRow agg = aggregate(t, new BigDecimal("1200"), new BigDecimal("1000"), new BigDecimal("200"));
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(viopSnap));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(shortDeriv));
        stubFx(entry, new BigDecimal("10"), t.toLocalDate(), new BigDecimal("12"));

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        PerformanceAssetDetail viop = result.get(0).details().stream()
                .filter(d -> "VIOP".equals(d.assetType()))
                .findFirst().orElseThrow();
        assertThat(viop.pnlByCcy().get("USD")).isNotNull();
        assertThat(viop.pnlByCcy().get("USD")).isPositive();   // direction-aware: a profiting SHORT reads +
        assertThat(viop.pnlByCcy().get("USD")).isCloseTo(new BigDecimal("33.33"), within(new BigDecimal("0.5")));
    }

    @Test
    void shouldNetOppositeDirectionsToZero_whenSameSymbolLongAndShortInAggregateUsdFrame() {
        // Arrange: same symbol, equal magnitude LONG + SHORT, same fallen price (100 -> 80) and FLAT FX (10).
        // A direction-blind aggregation would double the SHORT's notional change; direction-aware footprints
        // must net the two opposite K/Z to ~0 in the USD frame.
        LocalDate entry = LocalDate.of(2026, 5, 1);
        LocalDateTime t = LocalDateTime.of(2026, 5, 31, 0, 0);
        ViopContract contract = contract("XU030F", "10", "TRY");
        DerivativePosition longDeriv = openDerivative(contract, DerivativeDirection.LONG, "100", "1", entry);
        DerivativePosition shortDeriv = openDerivative(contract, DerivativeDirection.SHORT, "100", "1", entry);
        // LONG equity 800 (loss -200) + SHORT equity 1200 (profit +200) = 2000; notional per leg = 80*10 = 800.
        PortfolioAssetDailySnapshot viopSnap = viopAssetSnap(t, "XU030F", new BigDecimal("80"), new BigDecimal("1600"));
        PortfolioAggregateRow agg = aggregate(t, new BigDecimal("2000"), new BigDecimal("2000"), BigDecimal.ZERO);
        when(dailySnapshotRepository.findAggregateByPortfolio(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(agg));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(viopSnap));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(longDeriv, shortDeriv));
        stubFx(entry, new BigDecimal("10"), t.toLocalDate(), new BigDecimal("10"));

        // Act
        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        // Assert: opposite directions cancel — aggregate USD K/Z nets to ~0.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).pnlByCcy().get("USD")).isCloseTo(BigDecimal.ZERO, within(new BigDecimal("0.01")));
    }

    @Test
    void shouldLockClosedDerivativeUsdValueAcrossPostCloseDates_whenDailyPnlByCcy() {
        // Arrange: a CLOSED LONG VIOP (entry 100 -> close 110, size 10) whose realized USD PnL is FROZEN at its
        // close-date FX. Two post-close snapshot dates carry NO per-asset rows (fallback path) and DIFFERENT FX
        // (12 then 20). Pre-fix the closed lot's USD value was re-marked at each day's FX → ±0.0001 phantom dust.
        LocalDate entry = LocalDate.of(2026, 5, 1);
        LocalDate close = LocalDate.of(2026, 5, 10);
        ViopContract contract = contract("XU030F", "10", "TRY");
        DerivativePosition closedLong = closedDerivative(contract, DerivativeDirection.LONG,
                "100", "1", "110", entry, close);
        LocalDate d1 = LocalDate.of(2026, 5, 20);
        LocalDate d2 = LocalDate.of(2026, 5, 21);
        PortfolioDailySnapshot s1 = daySnapshot(d1, new BigDecimal("1100"));
        PortfolioDailySnapshot s2 = daySnapshot(d2, new BigDecimal("1100"));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closedLong));
        when(assetSnapshotRepository.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(PORTFOLIO_ID), any(), any())).thenReturn(List.of());
        // entry & close FX both 10 -> locked USD realized = 1100/10 - 1000/10 = +10; daily-date FX 12 then 20.
        java.util.Map<LocalDate, BigDecimal> fx = new java.util.HashMap<>();
        fx.put(entry, new BigDecimal("10"));
        fx.put(close, new BigDecimal("10"));
        fx.put(d1, new BigDecimal("12"));
        fx.put(d2, new BigDecimal("20"));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any())).thenReturn(fx);

        // Act
        java.util.Map<LocalDate, java.util.Map<String, BigDecimal>> result =
                service.dailyPnlByCcy(PORTFOLIO_ID, List.of(s1, s2));

        // Assert: the closed lot's USD value is IDENTICAL across both post-close dates -> daily delta exactly 0.
        BigDecimal usdD1 = result.get(d1).get("USD");
        BigDecimal usdD2 = result.get(d2).get("USD");
        assertThat(usdD1).isEqualByComparingTo(usdD2);
        assertThat(usdD2.subtract(usdD1)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(usdD1).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void shouldVaryOpenDerivativeUsdPnlWithPerDateFx_whenDailyPnlByCcy() {
        // Arrange (regression guard): an OPEN LONG VIOP MUST keep per-date FX, so its USD PnL differs across two
        // dates whose FX differs (12 then 20) even though its TRY value is unchanged. Asset rows per date carry the
        // open notional so perCcyInputs reconstructs the date's value.
        LocalDate entry = LocalDate.of(2026, 5, 1);
        ViopContract contract = contract("XU030F", "10", "TRY");
        DerivativePosition openLong = openDerivative(contract, DerivativeDirection.LONG, "100", "1", entry);
        LocalDate d1 = LocalDate.of(2026, 5, 20);
        LocalDate d2 = LocalDate.of(2026, 5, 21);
        PortfolioDailySnapshot s1 = daySnapshot(d1, new BigDecimal("1200"));
        PortfolioDailySnapshot s2 = daySnapshot(d2, new BigDecimal("1200"));
        PortfolioAssetDailySnapshot a1 = viopAssetSnap(d1.atStartOfDay(), "XU030F", new BigDecimal("120"), new BigDecimal("1200"));
        PortfolioAssetDailySnapshot a2 = viopAssetSnap(d2.atStartOfDay(), "XU030F", new BigDecimal("120"), new BigDecimal("1200"));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(openLong));
        when(assetSnapshotRepository.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(PORTFOLIO_ID), any(), any())).thenReturn(List.of(a1, a2));
        java.util.Map<LocalDate, BigDecimal> fx = new java.util.HashMap<>();
        fx.put(entry, new BigDecimal("10"));
        fx.put(d1, new BigDecimal("12"));
        fx.put(d2, new BigDecimal("20"));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any())).thenReturn(fx);

        // Act
        java.util.Map<LocalDate, java.util.Map<String, BigDecimal>> result =
                service.dailyPnlByCcy(PORTFOLIO_ID, List.of(s1, s2));

        // Assert: the OPEN lot's USD PnL is re-marked per date, so the two differ (per-date FX preserved).
        BigDecimal usdD1 = result.get(d1).get("USD");
        BigDecimal usdD2 = result.get(d2).get("USD");
        assertThat(usdD1).isNotEqualByComparingTo(usdD2);
    }

    @Test
    void shouldCountSinceClosedDerivativeAsOpen_onPreCloseDates_whenDailyPnlByCcy() {
        // Regression: a lot CLOSED LATER must count as OPEN on dates BEFORE its close. Pre-fix perCcyInputs used
        // `else if (closeDate == null)`, so a since-closed lot contributed 0 notional + no footprint on every
        // pre-close day → the aggregate USD frame had no value → $0, ramping only at the close ("Tümü broken,
        // VİOP-filter correct"). Now `else` treats closeDate-after-date as open.
        LocalDate entry = LocalDate.of(2026, 5, 1);
        LocalDate close = LocalDate.of(2026, 6, 1);
        ViopContract contract = contract("XU030F", "10", "TRY");
        DerivativePosition sinceClosed = closedDerivative(contract, DerivativeDirection.LONG, "100", "1", "130", entry, close);
        LocalDate d1 = LocalDate.of(2026, 5, 20);   // BEFORE close → the lot was still open
        LocalDate d2 = LocalDate.of(2026, 5, 21);
        PortfolioDailySnapshot s1 = daySnapshot(d1, new BigDecimal("1200"));
        PortfolioDailySnapshot s2 = daySnapshot(d2, new BigDecimal("1200"));
        PortfolioAssetDailySnapshot a1 = viopAssetSnap(d1.atStartOfDay(), "XU030F", new BigDecimal("120"), new BigDecimal("1200"));
        PortfolioAssetDailySnapshot a2 = viopAssetSnap(d2.atStartOfDay(), "XU030F", new BigDecimal("120"), new BigDecimal("1200"));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(sinceClosed));
        when(assetSnapshotRepository.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(PORTFOLIO_ID), any(), any())).thenReturn(List.of(a1, a2));
        java.util.Map<LocalDate, BigDecimal> fx = new java.util.HashMap<>();
        fx.put(entry, new BigDecimal("10"));
        fx.put(d1, new BigDecimal("12"));
        fx.put(d2, new BigDecimal("20"));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any())).thenReturn(fx);

        java.util.Map<LocalDate, java.util.Map<String, BigDecimal>> result =
                service.dailyPnlByCcy(PORTFOLIO_ID, List.of(s1, s2));

        // The since-closed lot is marked OPEN per-date on the pre-close dates → USD frame present and differs by FX
        // (pre-fix it contributed nothing → null/identical).
        BigDecimal usdD1 = result.get(d1) != null ? result.get(d1).get("USD") : null;
        BigDecimal usdD2 = result.get(d2) != null ? result.get(d2).get("USD") : null;
        assertThat(usdD1).isNotNull();
        assertThat(usdD2).isNotNull();
        assertThat(usdD1).isNotEqualByComparingTo(usdD2);
    }

    private PortfolioDailySnapshot daySnapshot(LocalDate date, BigDecimal totalValueTry) {
        return PortfolioDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID)
                .snapshotDate(date)
                .totalValueTry(totalValueTry)
                .createdAt(date.atStartOfDay())
                .build();
    }

    private void stubFx(LocalDate entryDate, BigDecimal entryFx, LocalDate pointDate, BigDecimal pointFx) {
        java.util.Map<LocalDate, BigDecimal> fx = new java.util.HashMap<>();
        fx.put(entryDate, entryFx);
        fx.put(pointDate, pointFx);
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any())).thenReturn(fx);
    }

    private PortfolioAssetDailySnapshot viopAssetSnap(LocalDateTime ts, String code,
                                                      BigDecimal unitPriceTry, BigDecimal marketValueTry) {
        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID)
                .assetType(AssetType.VIOP).assetCode(code)
                .quantity(BigDecimal.ONE)
                .unitPriceTry(unitPriceTry)
                .marketValueTry(marketValueTry)
                .totalCostTry(marketValueTry)
                .pnlTry(BigDecimal.ZERO)
                .snapshotDate(ts.toLocalDate())
                .createdAt(ts)
                .build();
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
