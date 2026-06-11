package com.finance.portfolio.service;
import com.finance.market.core.service.HistoricalPricingPort;

import com.finance.shared.service.AssetPricingPort;
import com.finance.portfolio.config.PortfolioProperties;



import com.finance.portfolio.model.AssetType;
import com.finance.common.model.MarketType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.repository.PortfolioRepository;
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
    @Mock private com.finance.portfolio.derivative.repository.DerivativePositionRepository derivativePositionRepository;
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
        lenient().when(derivativePositionRepository.findByPortfolioId(any())).thenReturn(List.of());
        PortfolioProperties props = new PortfolioProperties();
        service = new PortfolioBackfillService(
                portfolioRepository, positionRepository, derivativePositionRepository,
                dailySnapshotRepository, assetSnapshotRepository,
                assetPricingPort, calculator, new PortfolioBackfillTracker(props),
                new BackfillBatchCollector(assetSnapshotRepository, historicalPricingPort, calculator, props),
                transactionManager, props);
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
        when(calculator.buildAggregatedAssetSnapshotWithPrior(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any())).thenReturn(mockDailySnapshot());

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
        when(calculator.buildAggregatedAssetSnapshotWithPrior(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.backfillSinceDate(PORTFOLIO_ID, from);

        ArgumentCaptor<List<PortfolioPosition>> activeCaptor = activeCaptor();
        verify(calculator, times(3)).buildAggregateSnapshotAtFromRows(any(), any(), activeCaptor.capture(), any(), any(), any());
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
        when(calculator.buildAggregatedAssetSnapshotWithPrior(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.backfillSinceDate(PORTFOLIO_ID, from);

        ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(calculator, times(2)).buildAggregatedAssetSnapshotWithPrior(
                any(), any(), any(), any(), any(), any(), any(), priceCaptor.capture(), any());
        assertThat(priceCaptor.getAllValues()).containsExactly(new BigDecimal("40"), new BigDecimal("44"));
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
        when(calculator.buildAggregatedAssetSnapshotWithPrior(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.backfillSinceDate(PORTFOLIO_ID, from);

        ArgumentCaptor<BigDecimal> qtyCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(calculator).buildAggregatedAssetSnapshotWithPrior(any(), eq(AssetType.STOCK), eq("THYAO.IS"),
                any(), any(), qtyCaptor.capture(), costCaptor.capture(), eq(new BigDecimal("40")), any());
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
        when(assetPricingPort.getPricesTry(any()))
                .thenReturn(Map.of(new AssetPricingPort.AssetKey(MarketType.STOCK, "THYAO.IS"), new BigDecimal("55")));
        // THYAO was entered TODAY → snapshotToday anchors its daily to the entry price via a synthetic prior.
        when(calculator.buildAggregatedAssetSnapshotWithPrior(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.snapshotToday(PORTFOLIO_ID);

        verify(assetSnapshotRepository).saveAll(argThat(c -> ((java.util.Collection<?>) c).size() == 1));
        verify(dailySnapshotRepository).saveAll(argThat(c -> ((java.util.Collection<?>) c).size() == 1));
    }

    @Test
    void shouldWipeFromEntryDateThroughToday_whenLotChangedEventReceived() {
        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate today = LocalDate.now();
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        service.onLotChanged(new PortfolioBackfillService.LotChangedEvent(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS", from, true));

        verify(dailySnapshotRepository).deleteByPortfolioIdAndSnapshotDateBetween(PORTFOLIO_ID, from, today);
        verify(assetSnapshotRepository).deleteByPortfolioIdAndAssetTypeAndAssetCodeAndSnapshotDateBetween(
                PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS", from, today);
    }

    @Test
    void shouldWipeTodayOnly_whenLotChangedEventFromDateIsToday() {
        LocalDate today = LocalDate.now();
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        service.onLotChanged(new PortfolioBackfillService.LotChangedEvent(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS", today, true));

        verify(dailySnapshotRepository).deleteByPortfolioIdAndSnapshotDateBetween(PORTFOLIO_ID, today, today);
        verify(assetSnapshotRepository).deleteByPortfolioIdAndAssetTypeAndAssetCodeAndSnapshotDateBetween(
                PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS", today, today);
    }

    @Test
    void shouldNotWipeAnything_whenLotChangedEventFromDateIsInFuture() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        service.onLotChanged(new PortfolioBackfillService.LotChangedEvent(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS", tomorrow, true));

        verify(dailySnapshotRepository, never()).deleteByPortfolioIdAndSnapshotDateBetween(any(), any(), any());
        verify(assetSnapshotRepository, never()).deleteByPortfolioIdAndAssetTypeAndAssetCodeAndSnapshotDateBetween(
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldSnapshotTodayAggregate_whenOnlyDerivativePositionsExist() {
        LocalDate today = LocalDate.now();
        com.finance.portfolio.derivative.model.DerivativePosition dpos =
                com.finance.portfolio.derivative.model.DerivativePosition.builder()
                        .entryDate(today)
                        .entryPrice(new BigDecimal("4.95"))
                        .quantityLot(new BigDecimal("0.5"))
                        .direction(com.finance.portfolio.derivative.model.DerivativeDirection.LONG)
                        .build();
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(dpos));
        when(assetSnapshotRepository.findByPortfolioIdAndSnapshotDate(PORTFOLIO_ID, today)).thenReturn(List.of());
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any())).thenReturn(mockDailySnapshot());

        service.snapshotToday(PORTFOLIO_ID);

        verify(dailySnapshotRepository).saveAll(argThat(c -> ((java.util.Collection<?>) c).size() == 1));
    }

    @Test
    void shouldSkipTodayAggregate_whenNoPositionsAndNoDerivativesExist() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        service.snapshotToday(PORTFOLIO_ID);

        verify(dailySnapshotRepository, never()).saveAll(any());
    }

    @Test
    void shouldReSnapshotAllActiveAssets_whenSnapshotTodayCalledAfterLotChange_evenWhenSomeAssetsAlreadyHaveTodaySnapshots() {
        LocalDate today = LocalDate.now();
        PortfolioPosition existing = lot(AssetType.FUND, "BLH",
                new BigDecimal("9"), new BigDecimal("45"), today.minusDays(10).atStartOfDay());
        PortfolioPosition justAdded = lot(AssetType.FUND, "FIL",
                new BigDecimal("1"), new BigDecimal("3.4"), today.atStartOfDay());
        PortfolioAssetDailySnapshot earlierToday = PortfolioAssetDailySnapshot.builder()
                .portfolioId(PORTFOLIO_ID).assetType(AssetType.FUND).assetCode("BLH")
                .snapshotDate(today).createdAt(today.atStartOfDay().plusHours(9))
                .marketValueTry(new BigDecimal("420"))
                .build();
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(existing, justAdded));
        lenient().when(assetSnapshotRepository.findByPortfolioIdAndSnapshotDate(PORTFOLIO_ID, today))
                .thenReturn(List.of(earlierToday));
        when(assetPricingPort.getPricesTry(any())).thenReturn(Map.of(
                new AssetPricingPort.AssetKey(MarketType.FUND, "BLH"), new BigDecimal("47"),
                new AssetPricingPort.AssetKey(MarketType.FUND, "FIL"), new BigDecimal("3.5")));
        when(calculator.buildAggregatedAssetSnapshot(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregatedAssetSnapshotWithPrior(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any()))
                .thenReturn(mockDailySnapshot());

        service.snapshotToday(PORTFOLIO_ID);

        verify(assetSnapshotRepository).saveAll(argThat(c -> ((java.util.Collection<?>) c).size() == 2));
        // The older lot (BLH, entered 10 days ago) uses the real prior-day lookup.
        verify(calculator).buildAggregatedAssetSnapshot(any(), eq(AssetType.FUND), eq("BLH"),
                any(), any(), any(), any(), any());
        // The lot added TODAY (FIL) is anchored to its ENTRY PRICE (3.4) via a synthetic prior, so its daily K/Z
        // is (current − entry) × qty instead of the full value booked against a phantom zero prior.
        verify(calculator).buildAggregatedAssetSnapshotWithPrior(any(), eq(AssetType.FUND), eq("FIL"),
                any(), any(), any(), any(), any(),
                argThat(prior -> prior != null && prior.getUnitPriceTry() != null
                        && prior.getUnitPriceTry().compareTo(new BigDecimal("3.4")) == 0));
    }

    @Test
    void shouldEmitPreCloseAndZeroSnapshots_whenAllLotsOfAssetClosedOnSameDay() {
        LocalDate entryDay = LocalDate.now().minusDays(5);
        LocalDate closeDay = LocalDate.now().minusDays(2);
        PortfolioPosition closedLot = lot(AssetType.COMMODITY, "XAU.SPOT",
                new BigDecimal("10"), new BigDecimal("2800"), entryDay.atStartOfDay());
        closedLot.closeWith(closeDay.atStartOfDay(), new BigDecimal("3000"));
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closedLot));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(Map.of(
                        entryDay, new BigDecimal("2800"),
                        entryDay.plusDays(1), new BigDecimal("2850"),
                        entryDay.plusDays(2), new BigDecimal("2900"),
                        closeDay, new BigDecimal("3000")));
        when(calculator.buildAggregatedAssetSnapshotWithPrior(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());

        service.backfillAssetSinceDate(PORTFOLIO_ID, AssetType.COMMODITY, "XAU.SPOT", entryDay);

        verify(calculator).buildAggregatedAssetSnapshotWithPrior(
                eq(PORTFOLIO_ID), eq(AssetType.COMMODITY), eq("XAU.SPOT"), any(),
                eq(closeDay.atStartOfDay()),
                argThat(qty -> qty != null && qty.compareTo(new BigDecimal("10")) == 0),
                argThat(cost -> cost != null && cost.compareTo(new BigDecimal("28000")) == 0),
                argThat(price -> price != null && price.compareTo(new BigDecimal("3000")) == 0),
                any());
        verify(calculator).buildAggregatedAssetSnapshotWithPrior(
                eq(PORTFOLIO_ID), eq(AssetType.COMMODITY), eq("XAU.SPOT"), any(),
                eq(closeDay.atStartOfDay().plusSeconds(1)),
                argThat(qty -> qty != null && qty.compareTo(BigDecimal.ZERO) == 0),
                argThat(cost -> cost != null && cost.compareTo(BigDecimal.ZERO) == 0),
                argThat(price -> price != null && price.compareTo(new BigDecimal("3000")) == 0),
                any());
    }

    @Test
    void shouldDeleteOnlyActiveAssetTodayRows_whenSnapshotTodayRunsWithCloseDayMarkers() {
        // Arrange: one active asset (gets a fresh today row → must be deleted-then-reinserted) plus a
        // close-day pre-close + zero marker for an asset sold today (snapshotToday will NOT rebuild it).
        LocalDate today = LocalDate.now();
        PortfolioPosition active = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), today.minusDays(10).atStartOfDay());
        PortfolioAssetDailySnapshot activeTodayRow = PortfolioAssetDailySnapshot.builder()
                .id(10L).portfolioId(PORTFOLIO_ID).assetType(AssetType.STOCK).assetCode("THYAO.IS")
                .snapshotDate(today).createdAt(today.atStartOfDay().plusHours(9)).build();
        PortfolioAssetDailySnapshot closeMarkerPre = PortfolioAssetDailySnapshot.builder()
                .id(20L).portfolioId(PORTFOLIO_ID).assetType(AssetType.CRYPTO).assetCode("bitcoin")
                .snapshotDate(today).createdAt(today.atStartOfDay()).build();
        PortfolioAssetDailySnapshot closeMarkerZero = PortfolioAssetDailySnapshot.builder()
                .id(21L).portfolioId(PORTFOLIO_ID).assetType(AssetType.CRYPTO).assetCode("bitcoin")
                .snapshotDate(today).createdAt(today.atStartOfDay().plusSeconds(1)).build();
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(active));
        when(assetSnapshotRepository.findByPortfolioIdAndSnapshotDate(PORTFOLIO_ID, today))
                .thenReturn(List.of(activeTodayRow, closeMarkerPre, closeMarkerZero));
        when(assetPricingPort.getPricesTry(any()))
                .thenReturn(Map.of(new AssetPricingPort.AssetKey(MarketType.STOCK, "THYAO.IS"), new BigDecimal("55")));
        when(calculator.buildAggregatedAssetSnapshot(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAssetSnapshot());
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any()))
                .thenReturn(mockDailySnapshot());

        // Act
        service.snapshotToday(PORTFOLIO_ID);

        // Assert: only the active asset's stale today row is purged; close-day markers survive.
        ArgumentCaptor<List<Long>> deletedIds = idListCaptor();
        verify(assetSnapshotRepository).deleteAllByIdInBatch(deletedIds.capture());
        assertThat(deletedIds.getValue()).containsExactly(10L);
    }

    @Test
    void shouldNotDeleteCloseDayMarkers_whenOnlyClosedSpotLotChangedToday() {
        // Arrange: the asset's last lot was sold today, so backfillAssetSinceDate already wrote the
        // close-day markers and there is no active position. snapshotToday must leave them untouched.
        LocalDate today = LocalDate.now();
        PortfolioPosition closedLot = lot(AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"), today.minusDays(10).atStartOfDay());
        closedLot.closeWith(today.atStartOfDay(), new BigDecimal("55"));
        PortfolioAssetDailySnapshot closeMarkerZero = PortfolioAssetDailySnapshot.builder()
                .id(31L).portfolioId(PORTFOLIO_ID).assetType(AssetType.STOCK).assetCode("THYAO.IS")
                .snapshotDate(today).createdAt(today.atStartOfDay().plusSeconds(1)).build();
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closedLot));
        lenient().when(assetSnapshotRepository.findByPortfolioIdAndSnapshotDate(PORTFOLIO_ID, today))
                .thenReturn(List.of(closeMarkerZero));
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any()))
                .thenReturn(mockDailySnapshot());

        // Act
        service.snapshotToday(PORTFOLIO_ID);

        // Assert: nothing in the empty rebuild scope, so no delete fires and the marker survives.
        verify(assetSnapshotRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void shouldNotDeleteCloseDayDerivativeRows_whenDerivativeClosedToday() {
        // Arrange: a derivative closed today already has its close-day VIOP rows written by
        // rebuildPeerSnapshots; snapshotToday rebuilds only OPEN derivatives, so it must not delete them.
        LocalDate today = LocalDate.now();
        com.finance.market.viop.model.ViopContract contract =
                com.finance.market.viop.model.ViopContract.builder().symbol("F_USDTRY0625").build();
        com.finance.portfolio.derivative.model.DerivativePosition closedDeriv =
                com.finance.portfolio.derivative.model.DerivativePosition.builder()
                        .entryDate(today.minusDays(3))
                        .closeDate(today)
                        .entryPrice(new BigDecimal("4.95"))
                        .closePrice(new BigDecimal("5.10"))
                        .quantityLot(new BigDecimal("0.5"))
                        .viopContract(contract)
                        .direction(com.finance.portfolio.derivative.model.DerivativeDirection.LONG)
                        .build();
        PortfolioAssetDailySnapshot viopCloseZero = PortfolioAssetDailySnapshot.builder()
                .id(41L).portfolioId(PORTFOLIO_ID).assetType(AssetType.VIOP).assetCode("F_USDTRY0625")
                .snapshotDate(today).createdAt(today.atStartOfDay().plusSeconds(1)).build();
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closedDeriv));
        lenient().when(assetSnapshotRepository.findByPortfolioIdAndSnapshotDate(PORTFOLIO_ID, today))
                .thenReturn(List.of(viopCloseZero));
        when(calculator.buildAggregateSnapshotAtFromRows(any(), any(), any(), any(), any(), any()))
                .thenReturn(mockDailySnapshot());

        // Act
        service.snapshotToday(PORTFOLIO_ID);

        // Assert: the closed derivative is outside the rebuild scope, so its close-day rows are kept.
        verify(assetSnapshotRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void shouldSkipTodaySnapshot_whenBothExistingForToday() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio()));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        service.snapshotToday(PORTFOLIO_ID);

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

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Long>> idListCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
