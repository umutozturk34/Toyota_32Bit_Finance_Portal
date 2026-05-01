package com.finance.backend.service;

import com.finance.backend.model.AssetType;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import com.finance.backend.model.PortfolioDailySnapshot;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.backend.repository.PortfolioDailySnapshotRepository;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioBackfillServiceTest {

    private static final Long PORTFOLIO_ID = 1L;

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private PortfolioDailySnapshotRepository dailySnapshotRepository;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private HistoricalPricingPort historicalPricingPort;
    @Mock private AssetPricingPort assetPricingPort;
    @Mock private SnapshotCalculationService calculator;
    @Mock private PlatformTransactionManager transactionManager;

    private PortfolioBackfillService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(dailySnapshotRepository.findExistingDates(any(), any(), any())).thenReturn(List.of());
        lenient().when(assetSnapshotRepository.findExistingDates(any(), any(), any())).thenReturn(List.of());
        service = new PortfolioBackfillService(
                portfolioRepository, positionRepository,
                dailySnapshotRepository, assetSnapshotRepository,
                historicalPricingPort, assetPricingPort, calculator, new PortfolioBackfillTracker(),
                transactionManager);
    }

    @Test
    void shouldSkipBackfill_whenPortfolioMissing() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.empty());

        service.backfillSinceDate(PORTFOLIO_ID, LocalDate.now().minusDays(5));

        verify(positionRepository, never()).findByPortfolioId(any());
    }

    @Test
    void shouldSkipBackfill_whenFromDateNotInPast() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));

        service.backfillSinceDate(PORTFOLIO_ID, LocalDate.now());

        verify(positionRepository, never()).findByPortfolioId(any());
    }

    @Test
    void shouldSkipBackfill_whenNoPositionsExist() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        service.backfillSinceDate(PORTFOLIO_ID, LocalDate.now().minusDays(3));

        verify(historicalPricingPort, never()).getPriceSeries(any(), any(), any(), any());
    }

    @Test
    void shouldFillBothSnapshotsForEachActiveDay_whenPriceDataAvailable() {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        PortfolioPosition pos = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), from.atStartOfDay());
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(pos));
        when(historicalPricingPort.getPriceSeries(eq(MarketType.STOCK), eq("THYAO.IS"), eq(from), eq(yesterday)))
                .thenReturn(Map.of(from, new BigDecimal("45"), yesterday, new BigDecimal("50")));
        lenient().when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(any(), any())).thenReturn(false);
        lenient().when(assetSnapshotRepository.existsByPortfolioIdAndSnapshotDate(any(), any())).thenReturn(false);
        when(calculator.buildAggregatedAssetSnapshot(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAt(any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.backfillSinceDate(PORTFOLIO_ID, from);

        verify(assetSnapshotRepository).saveAll(argThat(c -> ((java.util.Collection<?>) c).size() == 2));
        verify(dailySnapshotRepository).saveAll(argThat(c -> ((java.util.Collection<?>) c).size() == 2));
    }

    @Test
    void shouldSkipDay_whenBothSnapshotsAlreadyExist() {
        LocalDate from = LocalDate.now().minusDays(2);
        PortfolioPosition pos = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), from.atStartOfDay());
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(pos));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(Map.of(from, new BigDecimal("45")));
        LocalDate end = LocalDate.now().minusDays(1);
        when(dailySnapshotRepository.findExistingDates(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(from, end));
        when(assetSnapshotRepository.findExistingDates(eq(PORTFOLIO_ID), any(), any()))
                .thenReturn(List.of(from, end));

        service.backfillSinceDate(PORTFOLIO_ID, from);

        verify(assetSnapshotRepository, never()).saveAll(any());
        verify(dailySnapshotRepository, never()).saveAll(any());
    }

    @Test
    void shouldExcludePositionsAddedAfterDay_whenAggregating() {
        LocalDate from = LocalDate.now().minusDays(3);
        LocalDate cutoff = from.plusDays(1);
        PortfolioPosition early = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), from.atStartOfDay());
        PortfolioPosition late = lot(AssetType.CRYPTO, "bitcoin",
                new BigDecimal("1"), new BigDecimal("2400000"), cutoff.atStartOfDay());
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(early, late));
        when(historicalPricingPort.getPriceSeries(eq(MarketType.STOCK), any(), any(), any()))
                .thenReturn(Map.of(from, new BigDecimal("45")));
        when(historicalPricingPort.getPriceSeries(eq(MarketType.CRYPTO), any(), any(), any()))
                .thenReturn(Map.of(cutoff, new BigDecimal("2500000")));
        lenient().when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(any(), any())).thenReturn(false);
        lenient().when(assetSnapshotRepository.existsByPortfolioIdAndSnapshotDate(any(), any())).thenReturn(false);
        when(calculator.buildAggregatedAssetSnapshot(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAt(any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.backfillSinceDate(PORTFOLIO_ID, from);

        ArgumentCaptor<List<PortfolioPosition>> activeCaptor = activeCaptor();
        verify(calculator, times(3)).buildAggregateSnapshotAt(any(), any(), activeCaptor.capture(), any());
        assertThat(activeCaptor.getAllValues().get(0)).extracting(PortfolioPosition::getAssetCode)
                .containsExactly("THYAO.IS");
        assertThat(activeCaptor.getAllValues().get(1)).extracting(PortfolioPosition::getAssetCode)
                .contains("THYAO.IS", "bitcoin");
    }

    @Test
    void shouldUseNearestEarlierPrice_whenExactDayMissingWithinLookback() {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate priceDay = from.minusDays(1);
        PortfolioPosition pos = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), from.atStartOfDay());
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(pos));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(Map.of(priceDay, new BigDecimal("44")));
        lenient().when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(any(), any())).thenReturn(false);
        lenient().when(assetSnapshotRepository.existsByPortfolioIdAndSnapshotDate(any(), any())).thenReturn(false);
        when(calculator.buildAggregatedAssetSnapshot(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAt(any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.backfillSinceDate(PORTFOLIO_ID, from);

        ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(calculator, times(2)).buildAggregatedAssetSnapshot(
                any(), any(), any(), any(), any(), any(), priceCaptor.capture());
        assertThat(priceCaptor.getAllValues()).allMatch(p -> p.compareTo(new BigDecimal("44")) == 0);
    }

    @Test
    void shouldAggregateMultipleLotsOfSameAsset_intoSingleSnapshotCall() {
        LocalDate from = LocalDate.now().minusDays(1);
        PortfolioPosition lotA = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), from.atStartOfDay());
        PortfolioPosition lotB = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("50"), new BigDecimal("60"), from.atStartOfDay());
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(lotA, lotB));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(Map.of(from, new BigDecimal("70")));
        lenient().when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(any(), any())).thenReturn(false);
        lenient().when(assetSnapshotRepository.existsByPortfolioIdAndSnapshotDate(any(), any())).thenReturn(false);
        when(calculator.buildAggregatedAssetSnapshot(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAt(any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.backfillSinceDate(PORTFOLIO_ID, from);

        ArgumentCaptor<BigDecimal> qtyCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(calculator).buildAggregatedAssetSnapshot(any(), eq(AssetType.STOCK), eq("THYAO.IS"),
                any(), qtyCaptor.capture(), costCaptor.capture(), eq(new BigDecimal("70")));
        assertThat(qtyCaptor.getValue()).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(costCaptor.getValue()).isEqualByComparingTo(new BigDecimal("7000.0000"));
    }

    @Test
    void shouldSnapshotToday_whenPositionsActiveAndNoExistingSnapshot() {
        LocalDate today = LocalDate.now();
        PortfolioPosition pos = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), today.atStartOfDay());
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(pos));
        lenient().when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(PORTFOLIO_ID, today)).thenReturn(false);
        lenient().when(assetSnapshotRepository.existsByPortfolioIdAndSnapshotDate(PORTFOLIO_ID, today)).thenReturn(false);
        when(assetPricingPort.getPricesTry(any()))
                .thenReturn(Map.of(new AssetPricingPort.AssetKey(MarketType.STOCK, "THYAO.IS"), new BigDecimal("55")));
        when(calculator.buildAggregatedAssetSnapshot(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAt(any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.snapshotToday(PORTFOLIO_ID);

        verify(assetSnapshotRepository).saveAll(argThat(c -> ((java.util.Collection<?>) c).size() == 1));
        verify(dailySnapshotRepository).saveAll(argThat(c -> ((java.util.Collection<?>) c).size() == 1));
    }

    @Test
    void shouldWipeSnapshotsAndRecompute_whenLotChangedEventReceived() {
        LocalDate from = LocalDate.now().minusDays(2);
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        service.onLotChanged(new PortfolioBackfillService.LotChangedEvent(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS", from, true));

        verify(dailySnapshotRepository).deleteByPortfolioIdAndSnapshotDateGreaterThanEqual(PORTFOLIO_ID, from);
        verify(assetSnapshotRepository).deleteByPortfolioIdAndSnapshotDateGreaterThanEqual(PORTFOLIO_ID, from);
    }

    @Test
    void shouldSkipTodaySnapshot_whenBothExistingForToday() {
        LocalDate today = LocalDate.now();
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        lenient().when(dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(PORTFOLIO_ID, today)).thenReturn(true);
        lenient().when(assetSnapshotRepository.existsByPortfolioIdAndSnapshotDate(PORTFOLIO_ID, today)).thenReturn(true);

        service.snapshotToday(PORTFOLIO_ID);

        verify(positionRepository, never()).findByPortfolioId(any());
        verify(assetSnapshotRepository, never()).saveAll(any());
        verify(dailySnapshotRepository, never()).saveAll(any());
    }

    private static Portfolio portfolio() {
        return Portfolio.builder().id(PORTFOLIO_ID).userSub("user-1").build();
    }

    private static PortfolioPosition lot(AssetType type, String code, BigDecimal qty,
                                          BigDecimal entryPrice, LocalDateTime entryDate) {
        return PortfolioPosition.builder()
                .portfolioId(PORTFOLIO_ID)
                .assetType(type).assetCode(code)
                .quantity(qty)
                .entryDate(entryDate)
                .entryPrice(entryPrice)
                .build();
    }

    private static PortfolioAssetDailySnapshot mockAssetSnapshot() {
        return PortfolioAssetDailySnapshot.builder().build();
    }

    private static PortfolioDailySnapshot mockDailySnapshot() {
        return PortfolioDailySnapshot.builder().build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<PortfolioPosition>> activeCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
