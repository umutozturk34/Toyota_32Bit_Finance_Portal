package com.finance.backend.service;

import com.finance.backend.dto.response.AssetSeriesPoint;
import com.finance.backend.dto.response.PerformanceEvent;
import com.finance.backend.dto.response.PerformancePoint;
import com.finance.backend.mapper.PortfolioSnapshotMapper;
import com.finance.backend.model.AssetType;
import com.finance.backend.model.PerformanceEventType;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import com.finance.backend.model.PortfolioDailySnapshot;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.backend.repository.PortfolioDailySnapshotRepository;
import com.finance.backend.repository.PortfolioPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioPerformanceServiceTest {

    private static final Long PORTFOLIO_ID = 1L;

    @Mock private PortfolioDailySnapshotRepository dailySnapshotRepository;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private PortfolioSnapshotMapper snapshotMapper;

    private PortfolioPerformanceService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioPerformanceService(
                dailySnapshotRepository, assetSnapshotRepository, positionRepository, snapshotMapper);
    }

    @Test
    void shouldReturnAggregatePerformancePointsWithDetails_whenNoAssetTypeFilter() {
        LocalDateTime t = LocalDateTime.of(2026, 4, 10, 23, 0);
        PortfolioDailySnapshot daily = dailySnapshot(t,
                new BigDecimal("3000"), new BigDecimal("100"), new BigDecimal("3.5000"));
        PortfolioAssetDailySnapshot stockSnap = assetSnap(t, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("2000"), new BigDecimal("80"));
        PortfolioAssetDailySnapshot cryptoSnap = assetSnap(t, AssetType.CRYPTO, "bitcoin",
                new BigDecimal("1000"), new BigDecimal("20"));

        when(dailySnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(daily));
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
        PortfolioDailySnapshot daily = dailySnapshot(now,
                new BigDecimal("4000"), new BigDecimal("0"), BigDecimal.ZERO);
        PortfolioPosition newLot = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), now.minusDays(1));

        when(dailySnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(daily));
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
    void shouldEmitMarketUpAndDownEvents_whenAssetValuesChangeBetweenSnapshots() {
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 10, 23, 0);
        LocalDateTime t2 = t1.plusDays(1);
        PortfolioDailySnapshot d1 = dailySnapshot(t1,
                new BigDecimal("100"), new BigDecimal("0"), BigDecimal.ZERO);
        PortfolioDailySnapshot d2 = dailySnapshot(t2,
                new BigDecimal("120"), new BigDecimal("20"), new BigDecimal("20.0000"));
        PortfolioAssetDailySnapshot snap1 = assetSnap(t1, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), BigDecimal.ZERO);
        PortfolioAssetDailySnapshot snap2 = assetSnap(t2, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("120"), new BigDecimal("20"));

        when(dailySnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(d1, d2));
        when(assetSnapshotRepository.findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(snap1, snap2));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        List<PerformancePoint> result = service.getPerformance(PORTFOLIO_ID, "1M", null);

        List<PerformanceEvent> dayTwoEvents = result.get(1).events();
        assertThat(dayTwoEvents).hasSize(1);
        assertThat(dayTwoEvents.get(0).type()).isEqualTo(PerformanceEventType.MARKET_UP);
        assertThat(dayTwoEvents.get(0).valueTry()).isEqualByComparingTo(new BigDecimal("20"));
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
    void shouldDelegateToAssetSnapshotMapper_whenFetchingAssetSeries() {
        PortfolioAssetDailySnapshot snap = assetSnap(
                LocalDateTime.now(), AssetType.CRYPTO, "bitcoin",
                new BigDecimal("2500000"), new BigDecimal("100000"));
        AssetSeriesPoint expected = new AssetSeriesPoint(LocalDateTime.now(),
                new BigDecimal("2500000"), new BigDecimal("2500000"), new BigDecimal("100000"));

        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(PORTFOLIO_ID), eq(AssetType.CRYPTO), eq("bitcoin"), any(), any()))
                .thenReturn(List.of(snap));
        when(snapshotMapper.toAssetSeriesPoints(List.of(snap))).thenReturn(List.of(expected));

        List<AssetSeriesPoint> result = service.getAssetSeries(PORTFOLIO_ID, "CRYPTO", "bitcoin", "1M");

        assertThat(result).containsExactly(expected);
    }

    private PortfolioDailySnapshot dailySnapshot(LocalDateTime ts, BigDecimal totalValue,
                                                 BigDecimal totalPnl, BigDecimal pnlPercent) {
        return PortfolioDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID)
                .snapshotDate(ts.toLocalDate())
                .createdAt(ts)
                .totalValueTry(totalValue)
                .totalCostTry(totalValue.subtract(totalPnl))
                .totalPnlTry(totalPnl)
                .pnlPercent(pnlPercent)
                .build();
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
}
