package com.finance.portfolio.derivative.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.service.SnapshotCalculationService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DerivativeSnapshotMaintenanceTest {

    private static final Long PORTFOLIO_ID = 7L;

    @Mock private ViopCandleRepository candleRepository;
    @Mock private HistoricalPricingPort historicalPricingPort;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private SnapshotCalculationService snapshotCalculator;
    @Mock private DerivativePositionRepository derivativePositionRepository;

    private DerivativeSnapshotMaintenance maintenance;

    @BeforeEach
    void setUp() {
        maintenance = new DerivativeSnapshotMaintenance(candleRepository, historicalPricingPort,
                assetSnapshotRepository, snapshotCalculator, derivativePositionRepository);
    }

    private ViopContract contract(String symbol, String currency) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .contractSize(BigDecimal.ONE)
                .currency(currency)
                .active(true)
                .build();
    }

    private DerivativePosition position(ViopContract c, LocalDate from, LocalDate to,
                                          BigDecimal closePrice, String entryPrice) {
        return DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .entryPrice(new BigDecimal(entryPrice))
                .quantityLot(BigDecimal.ONE)
                .closeDate(to)
                .closePrice(closePrice)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
    }

    @Test
    void shouldReturn_whenContractIsNull() {
        DerivativePosition dp = DerivativePosition.builder()
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.now())
                .entryPrice(BigDecimal.ONE)
                .quantityLot(BigDecimal.ONE)
                .build();

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldReturn_whenEntryDateNull() {
        ViopContract c = contract("XU030F", "TRY");
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryPrice(BigDecimal.ONE)
                .quantityLot(BigDecimal.ONE)
                .build();

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldReturn_whenFromIsAfterTo() {
        ViopContract c = contract("XU030F", "TRY");
        DerivativePosition dp = position(c, LocalDate.of(2024, 6, 5), LocalDate.of(2024, 6, 1),
                new BigDecimal("100"), "100");

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldBuildSnapshotsAndSave_whenContractIsTryAndCandlesExist() {
        ViopContract c = contract("XU030F", "TRY");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 3);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .entryPrice(new BigDecimal("100"))
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                eq("XU030F"), any(), any())).thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("100")).build(),
                        ViopCandle.builder().candleDate(from.plusDays(1).atStartOfDay()).close(new BigDecimal("110")).build()
                ));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(eq(PORTFOLIO_ID), eq(dp), any(), any(), any(), any()))
                .thenAnswer(inv -> PortfolioAssetDailySnapshot.builder().build());

        maintenance.backfillSnapshots(dp);

        ArgumentCaptor<List<PortfolioAssetDailySnapshot>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(assetSnapshotRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
    }

    @Test
    void shouldSkipDateWhenCloseIsUnknown_whenNoCandleAndNoLastKnown() {
        ViopContract c = contract("XU030F", "TRY");
        LocalDate from = LocalDate.of(2024, 6, 1);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldUseClosePriceOverride_whenAtCloseDate() {
        ViopContract c = contract("XU030F", "TRY");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate close = LocalDate.of(2024, 6, 1);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .closeDate(close.plusDays(1))
                .closePrice(new BigDecimal("130"))
                .entryPrice(new BigDecimal("100"))
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("120")).build()
                ));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any(), any()))
                .thenReturn(PortfolioAssetDailySnapshot.builder().build());

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository).saveAll(anyList());
    }

    @Test
    void shouldFetchFxSeries_whenContractIsForeign() {
        ViopContract c = contract("F_XAUUSD0625", "USD");
        LocalDate from = LocalDate.of(2024, 6, 1);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .entryPrice(new BigDecimal("3000"))
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of(ViopCandle.builder()
                        .candleDate(from.atStartOfDay()).close(new BigDecimal("10")).build()));
        when(historicalPricingPort.getPriceSeries(eq(MarketType.FOREX), eq("USD"), any(), any()))
                .thenReturn(Map.of(from, new BigDecimal("30")));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any(), any()))
                .thenReturn(PortfolioAssetDailySnapshot.builder().build());

        maintenance.backfillSnapshots(dp);

        verify(historicalPricingPort).getPriceSeries(eq(MarketType.FOREX), eq("USD"), any(), any());
        verify(assetSnapshotRepository).saveAll(anyList());
    }

    @Test
    void shouldFallbackToLastFxRate_whenDailyFxIsMissing() {
        ViopContract c = contract("F_XAUUSD0625", "USD");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 2);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .entryPrice(new BigDecimal("3000"))
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("10")).build(),
                        ViopCandle.builder().candleDate(to.atStartOfDay()).close(new BigDecimal("11")).build()
                ));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(Map.of(from, new BigDecimal("30")));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any(), any()))
                .thenReturn(PortfolioAssetDailySnapshot.builder().build());

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository).saveAll(anyList());
    }

    @Test
    void shouldDoNothing_whenSymbolNull() {
        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, null);

        verify(assetSnapshotRepository, never()).findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldDoNothing_whenNoSnapshotsFound() {
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of());

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        verify(assetSnapshotRepository, never()).saveAll(anyList());
        verify(assetSnapshotRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    void shouldDoNothing_whenAllGroupsHaveSingleSnapshot() {
        LocalDateTime ts = LocalDateTime.of(2024, 6, 1, 12, 0);
        PortfolioAssetDailySnapshot snap = PortfolioAssetDailySnapshot.builder()
                .id(1L)
                .createdAt(ts)
                .build();
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of(snap));

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        verify(assetSnapshotRepository, never()).saveAll(anyList());
        verify(assetSnapshotRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    void shouldMergeDuplicates_whenTwoSnapshotsShareCreatedAt() {
        LocalDateTime ts = LocalDateTime.of(2024, 6, 1, 12, 0);
        PortfolioAssetDailySnapshot a = PortfolioAssetDailySnapshot.builder()
                .id(1L).createdAt(ts)
                .marketValueTry(new BigDecimal("100"))
                .pnlTry(new BigDecimal("10"))
                .quantity(new BigDecimal("2"))
                .totalCostTry(new BigDecimal("80"))
                .unitPriceTry(new BigDecimal("50"))
                .dailyPnlTry(new BigDecimal("5"))
                .build();
        PortfolioAssetDailySnapshot b = PortfolioAssetDailySnapshot.builder()
                .id(2L).createdAt(ts)
                .marketValueTry(new BigDecimal("200"))
                .pnlTry(new BigDecimal("20"))
                .quantity(new BigDecimal("4"))
                .totalCostTry(new BigDecimal("160"))
                .unitPriceTry(new BigDecimal("50"))
                .dailyPnlTry(new BigDecimal("10"))
                .build();
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of(a, b));

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        verify(assetSnapshotRepository).saveAll(anyList());
        verify(assetSnapshotRepository).deleteAllByIdInBatch(anyList());
        assertThat(a.getMarketValueTry()).isEqualByComparingTo("300");
        assertThat(a.getQuantity()).isEqualByComparingTo("6");
        assertThat(a.getTotalCostTry()).isEqualByComparingTo("240");
        assertThat(a.getPnlTry()).isEqualByComparingTo("30");
        assertThat(a.getDailyPnlTry()).isEqualByComparingTo("15");
    }

    @Test
    void shouldKeepNullDaily_whenAllDuplicatesHaveNullDailyPnl() {
        LocalDateTime ts = LocalDateTime.of(2024, 6, 1, 12, 0);
        PortfolioAssetDailySnapshot a = PortfolioAssetDailySnapshot.builder()
                .id(1L).createdAt(ts)
                .marketValueTry(new BigDecimal("100"))
                .quantity(new BigDecimal("2"))
                .totalCostTry(new BigDecimal("80"))
                .unitPriceTry(new BigDecimal("50"))
                .pnlTry(BigDecimal.ZERO)
                .build();
        PortfolioAssetDailySnapshot b = PortfolioAssetDailySnapshot.builder()
                .id(2L).createdAt(ts)
                .marketValueTry(new BigDecimal("200"))
                .quantity(new BigDecimal("4"))
                .totalCostTry(new BigDecimal("160"))
                .unitPriceTry(new BigDecimal("50"))
                .pnlTry(BigDecimal.ZERO)
                .build();
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of(a, b));

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        assertThat(a.getDailyPnlTry()).isNull();
        assertThat(a.getDailyPnlPercent()).isNull();
    }

    @Test
    void shouldKeepKeeperUnitPrice_whenMergedQuantityIsZero() {
        LocalDateTime ts = LocalDateTime.of(2024, 6, 1, 12, 0);
        PortfolioAssetDailySnapshot a = PortfolioAssetDailySnapshot.builder()
                .id(1L).createdAt(ts)
                .marketValueTry(BigDecimal.ZERO)
                .quantity(BigDecimal.ZERO)
                .totalCostTry(BigDecimal.ZERO)
                .unitPriceTry(new BigDecimal("42"))
                .pnlTry(BigDecimal.ZERO)
                .build();
        PortfolioAssetDailySnapshot b = PortfolioAssetDailySnapshot.builder()
                .id(2L).createdAt(ts)
                .marketValueTry(BigDecimal.ZERO)
                .quantity(BigDecimal.ZERO)
                .totalCostTry(BigDecimal.ZERO)
                .unitPriceTry(new BigDecimal("99"))
                .pnlTry(BigDecimal.ZERO)
                .build();
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of(a, b));

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        assertThat(a.getUnitPriceTry()).isEqualByComparingTo("42");
    }

    @Test
    void shouldReturnNull_whenSeriesIsNull() {
        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(null, LocalDate.now());

        assertThat(rate).isNull();
    }

    @Test
    void shouldReturnNull_whenSeriesIsEmpty() {
        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(Map.of(), LocalDate.now());

        assertThat(rate).isNull();
    }

    @Test
    void shouldReturnExactDayRate_whenSeriesHasTarget() {
        LocalDate target = LocalDate.of(2024, 6, 1);
        Map<LocalDate, BigDecimal> series = Map.of(target, new BigDecimal("30"));

        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(series, target);

        assertThat(rate).isEqualByComparingTo("30");
    }

    @Test
    void shouldWalkBackUpTo30Days_whenPriorRateExists() {
        LocalDate target = LocalDate.of(2024, 6, 30);
        Map<LocalDate, BigDecimal> series = Map.of(target.minusDays(10), new BigDecimal("28"));

        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(series, target);

        assertThat(rate).isEqualByComparingTo("28");
    }

    @Test
    void shouldReturnNull_whenAllRatesOlderThan30Days() {
        LocalDate target = LocalDate.of(2024, 6, 30);
        Map<LocalDate, BigDecimal> series = Map.of(target.minusDays(60), new BigDecimal("28"));

        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(series, target);

        assertThat(rate).isNull();
    }
}
