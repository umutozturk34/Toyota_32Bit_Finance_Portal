package com.finance.portfolio.service;

import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.shared.event.EventPublisherPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotServiceTest {

    @Mock private SnapshotCalculationService calculator;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private PortfolioDailySnapshotRepository dailySnapshotRepository;
    @Mock private com.finance.portfolio.derivative.repository.DerivativePositionRepository derivativePositionRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @SuppressWarnings("unchecked")
    @Mock private ObjectProvider<EventPublisherPort> events;
    @Mock private EventPublisherPort eventPublisher;

    private PortfolioSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioSnapshotService(calculator, portfolioRepository, positionRepository,
                derivativePositionRepository, assetSnapshotRepository, dailySnapshotRepository, transactionTemplate, events);
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> cb = inv.getArgument(0);
            cb.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void onMarketUpdate_skipsPortfolio_whenNoMatchingPositions() {
        Portfolio portfolio = portfolio(1L);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(positionRepository.findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
                1L, TrackedAssetType.STOCK, BigDecimal.ZERO)).thenReturn(List.of());

        service.onMarketUpdate(MarketType.STOCK);

        verify(assetSnapshotRepository, never()).save(any());
        verify(dailySnapshotRepository, never()).save(any());
    }

    @Test
    void onMarketUpdate_savesEachAssetSnapshotProducedByCalculator_thenAggregate() {
        Portfolio portfolio = portfolio(1L);
        PortfolioPosition p1 = position();
        PortfolioPosition p2 = position();
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(positionRepository.findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
                1L, TrackedAssetType.STOCK, BigDecimal.ZERO)).thenReturn(List.of(p1, p2));
        when(calculator.buildAssetSnapshotsForPositions(any(), any(), any())).thenReturn(
                List.of(mock(PortfolioAssetDailySnapshot.class), mock(PortfolioAssetDailySnapshot.class)));
        when(calculator.buildAggregateSnapshot(any(), any())).thenReturn(mock(PortfolioDailySnapshot.class));

        service.onMarketUpdate(MarketType.STOCK);

        verify(assetSnapshotRepository, times(2)).save(any());
        verify(dailySnapshotRepository).save(any());
    }

    @Test
    void generateDailySnapshots_skipsPortfolioWithExistingDailyRow() {
        Portfolio portfolio = portfolio(1L);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(1L, LocalDate.now())).thenReturn(true);

        service.generateDailySnapshots("scheduler");

        verify(positionRepository, never()).findByPortfolioIdAndQuantityGreaterThan(any(), any());
    }

    @Test
    void generateDailySnapshots_buildsFullSnapshot_whenNotYetGeneratedAndPositionsExist() {
        Portfolio portfolio = portfolio(1L);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(1L, LocalDate.now())).thenReturn(false);
        when(positionRepository.findByPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO))
                .thenReturn(List.of(position()));
        when(calculator.buildAssetSnapshotsForPositions(any(), any(), any()))
                .thenReturn(List.of(mock(PortfolioAssetDailySnapshot.class)));
        when(calculator.buildAggregateSnapshot(any(), any())).thenReturn(mock(PortfolioDailySnapshot.class));

        service.generateDailySnapshots("scheduler");

        verify(assetSnapshotRepository).save(any());
        verify(dailySnapshotRepository).save(any());
    }

    @Test
    void generateDailySnapshots_skipsBuild_whenNoPositions() {
        Portfolio portfolio = portfolio(1L);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(1L, LocalDate.now())).thenReturn(false);
        when(positionRepository.findByPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO))
                .thenReturn(List.of());

        service.generateDailySnapshots("scheduler");

        verify(assetSnapshotRepository, never()).save(any());
        verify(dailySnapshotRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateDailySnapshots_publishesEvent_whenAtLeastOnePortfolioExistsAndPublisherAvailable() {
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio(1L)));
        when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(1L, LocalDate.now())).thenReturn(true);
        doAnswer(inv -> {
            ((Consumer<EventPublisherPort>) inv.getArgument(0)).accept(eventPublisher);
            return null;
        }).when(events).ifAvailable(any());

        service.generateDailySnapshots("scheduler");

        verify(eventPublisher).publish(any(PortfolioUpdatedEvent.class));
    }

    @Test
    void generateDailySnapshots_skipsEventPublish_whenNoPortfolios() {
        when(portfolioRepository.findAll()).thenReturn(List.of());

        service.generateDailySnapshots("scheduler");

        verify(events, never()).ifAvailable(any());
    }

    @Test
    void onMarketUpdate_returnsImmediately_whenMarketTypeIsNotMappable() {
        service.onMarketUpdate(MarketType.BOND);

        verify(portfolioRepository, never()).findAll();
        verify(assetSnapshotRepository, never()).save(any());
    }

    @Test
    void onMarketUpdate_buildsDerivativeSnapshots_whenViopMarketTriggered() {
        Portfolio portfolio = portfolio(1L);
        com.finance.portfolio.derivative.model.DerivativePosition deriv =
                org.mockito.Mockito.mock(com.finance.portfolio.derivative.model.DerivativePosition.class);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(deriv));
        when(calculator.buildDerivativeAssetSnapshot(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(deriv), any()))
                .thenReturn(mock(PortfolioAssetDailySnapshot.class));
        when(calculator.buildAggregateSnapshot(any(), any())).thenReturn(mock(PortfolioDailySnapshot.class));

        service.onMarketUpdate(MarketType.VIOP);

        verify(assetSnapshotRepository).save(any());
        verify(dailySnapshotRepository).save(any());
    }

    @Test
    void onMarketUpdate_viopSkipsSave_whenNoDerivativePositions() {
        Portfolio portfolio = portfolio(1L);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of());
        when(calculator.buildAggregateSnapshot(any(), any())).thenReturn(mock(PortfolioDailySnapshot.class));

        service.onMarketUpdate(MarketType.VIOP);

        verify(assetSnapshotRepository, never()).save(any());
        verify(dailySnapshotRepository).save(any());
    }

    @Test
    void onMarketUpdate_skipsNullDerivativeSnapshot_whenCalculatorReturnsNull() {
        Portfolio portfolio = portfolio(1L);
        com.finance.portfolio.derivative.model.DerivativePosition deriv =
                org.mockito.Mockito.mock(com.finance.portfolio.derivative.model.DerivativePosition.class);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(deriv));
        when(calculator.buildDerivativeAssetSnapshot(any(), any(), any())).thenReturn(null);
        when(calculator.buildAggregateSnapshot(any(), any())).thenReturn(mock(PortfolioDailySnapshot.class));

        service.onMarketUpdate(MarketType.VIOP);

        verify(assetSnapshotRepository, never()).save(any());
    }

    @Test
    void generateDailySnapshots_includesDerivatives_whenFullSnapshotIsGenerated() {
        Portfolio portfolio = portfolio(1L);
        com.finance.portfolio.derivative.model.DerivativePosition deriv =
                org.mockito.Mockito.mock(com.finance.portfolio.derivative.model.DerivativePosition.class);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(1L, LocalDate.now())).thenReturn(false);
        when(positionRepository.findByPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO))
                .thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(deriv));
        when(calculator.buildDerivativeAssetSnapshot(any(), any(), any())).thenReturn(mock(PortfolioAssetDailySnapshot.class));
        when(calculator.buildAggregateSnapshot(any(), any())).thenReturn(mock(PortfolioDailySnapshot.class));

        service.generateDailySnapshots("scheduler");

        verify(assetSnapshotRepository).save(any());
        verify(dailySnapshotRepository).save(any());
    }

    @Test
    void generateDailySnapshots_skipsNullDerivativeSnapshot_whenCalculatorReturnsNull() {
        Portfolio portfolio = portfolio(1L);
        com.finance.portfolio.derivative.model.DerivativePosition deriv =
                org.mockito.Mockito.mock(com.finance.portfolio.derivative.model.DerivativePosition.class);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(1L, LocalDate.now())).thenReturn(false);
        when(positionRepository.findByPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO))
                .thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(deriv));
        when(calculator.buildDerivativeAssetSnapshot(any(), any(), any())).thenReturn(null);
        when(calculator.buildAggregateSnapshot(any(), any())).thenReturn(mock(PortfolioDailySnapshot.class));

        service.generateDailySnapshots("scheduler");

        verify(assetSnapshotRepository, never()).save(any());
        verify(dailySnapshotRepository).save(any());
    }

    private Portfolio portfolio(Long id) {
        Portfolio p = Portfolio.builder().id(id).userSub("u").name("My").build();
        return p;
    }

    private PortfolioPosition position() {
        return org.mockito.Mockito.mock(PortfolioPosition.class);
    }

    private static PortfolioPosition lot(AssetType type, String code, BigDecimal qty, BigDecimal entryPrice) {
        return PortfolioPosition.builder()
                .assetType(type).assetCode(code)
                .quantity(qty)
                .entryPrice(entryPrice)
                .entryDate(LocalDateTime.now())
                .build();
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }

    @Test
    void onMarketUpdate_excludesClosedPositions_whenBuildingAssetSnapshots() {
        Portfolio portfolio = portfolio(1L);
        PortfolioPosition open = lot(AssetType.FUND, "BLH", new BigDecimal("9"), new BigDecimal("45"));
        PortfolioPosition closed = lot(AssetType.FUND, "FIL", new BigDecimal("1"), new BigDecimal("3"));
        closed.closeWith(LocalDateTime.now().minusDays(1), new BigDecimal("3.4"));
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(positionRepository.findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
                1L, TrackedAssetType.FUND, BigDecimal.ZERO)).thenReturn(List.of(open, closed));
        when(calculator.buildAssetSnapshotsForPositions(any(), any(), any()))
                .thenReturn(List.of(mock(PortfolioAssetDailySnapshot.class)));
        when(calculator.buildAggregateSnapshot(any(), any())).thenReturn(mock(PortfolioDailySnapshot.class));

        service.onMarketUpdate(MarketType.FUND);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PortfolioPosition>> positionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(calculator).buildAssetSnapshotsForPositions(eq(1L), positionsCaptor.capture(), any());
        assertThat(positionsCaptor.getValue()).containsExactly(open);
    }

    @Test
    void onMarketUpdate_passesAllPositionsToCalculator_andSavesEachReturnedAggregatedSnapshot() {
        Portfolio portfolio = portfolio(1L);
        PortfolioPosition lotA = lot(AssetType.FUND, "BND", new BigDecimal("100"), new BigDecimal("3.00"));
        PortfolioPosition lotB = lot(AssetType.FUND, "BND", new BigDecimal("50"), new BigDecimal("3.10"));
        PortfolioAssetDailySnapshot aggregated = mock(PortfolioAssetDailySnapshot.class);
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(positionRepository.findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
                1L, TrackedAssetType.FUND, BigDecimal.ZERO)).thenReturn(List.of(lotA, lotB));
        when(calculator.buildAssetSnapshotsForPositions(any(), any(), any())).thenReturn(List.of(aggregated));
        when(calculator.buildAggregateSnapshot(any(), any())).thenReturn(mock(PortfolioDailySnapshot.class));

        service.onMarketUpdate(MarketType.FUND);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PortfolioPosition>> positionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(calculator).buildAssetSnapshotsForPositions(eq(1L), positionsCaptor.capture(), any());
        assertThat(positionsCaptor.getValue()).containsExactly(lotA, lotB);
        verify(assetSnapshotRepository, times(1)).save(aggregated);
    }
}
