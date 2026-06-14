package com.finance.market.bond.service;

import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.market.bond.mapper.BondMapper;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.service.AssetRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BondRateHistoryServiceTest {

    @Mock private BondMapper bondMapper;
    @Mock private BondRepository bondRepository;
    @Mock private BondRateHistoryRepository rateHistoryRepository;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Bond> bondCacheService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private BondRateFetcher rateFetcher;
    @Mock private AssetRegistryService assetRegistry;

    private BondRateHistoryService service;

    @BeforeEach
    void setUp() {
        BondProperties props = new BondProperties();
        service = new BondRateHistoryService(bondMapper, bondRepository, rateHistoryRepository,
                bondCacheService, transactionTemplate, rateFetcher, assetRegistry, props);
    }

    @SuppressWarnings("unchecked")
    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<Bond>>getArgument(0).doInTransaction(null));
        org.mockito.Mockito.doAnswer(inv -> {
            inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private BondSnapshotDto dto(String series, String isin, BigDecimal coupon, LocalDate maturity) {
        return new BondSnapshotDto(series, isin, new BigDecimal("100"), coupon,
                maturity, maturity.plusYears(5), "Bond " + series);
    }

    private Bond bond(String seriesCode) {
        Bond b = new Bond();
        b.setSeriesCode(seriesCode);
        return b;
    }

    private Instrument bondAsset(String code) {
        return Instrument.builder().marketType(MarketType.BOND).assetCode(code).build();
    }

    private BondRateHistory rateRecord(String isin, LocalDate date, BigDecimal coupon) {
        return BondRateHistory.builder()
                .isinCode(isin)
                .rateDate(date)
                .couponRate(coupon)
                .build();
    }

    @Test
    void processBatch_persistsSnapshot_andSkipsRateHistory_forZeroCoupon() {
        BondSnapshotDto d = dto("S1", "ISIN1", BigDecimal.ZERO, LocalDate.of(2020, 1, 1));
        Bond entity = bond("S1");
        when(bondRepository.findById("S1")).thenReturn(Optional.empty());
        when(bondMapper.toEntity(eq(d), any())).thenReturn(entity);
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(entity)).thenReturn(entity);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processBatch(List.of(d), LocalDateTime.now());

        verify(rateFetcher, never()).fetchBatch(any());
        verify(rateHistoryRepository, never()).saveAll(any());
        verify(bondCacheService).putSnapshot("S1", entity);
    }

    @Test
    void processBatch_fetchesPriceHistoryWithNullCoupon_forDiscountBill() {
        // A TRB ISIN is a zero-coupon discount bill: sanitizeCouponRate forces couponRate to 0. It must still
        // be fetched (it has a price series), and its stored row must carry a null coupon (the EVDS .ORAN value
        // is days-to-maturity, not a coupon) while keeping the price.
        BondSnapshotDto d = dto("S1", "TRB170626T13", new BigDecimal("308"), LocalDate.of(2025, 8, 13));
        Bond entity = bond("S1");
        when(bondRepository.findById("S1")).thenReturn(Optional.empty());
        when(bondMapper.toEntity(eq(d), any())).thenReturn(entity);
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(entity)).thenReturn(entity);
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc("TRB170626T13")).thenReturn(Optional.empty());
        when(rateFetcher.fetchBatch(any())).thenReturn(Map.of("TRB170626T13", new java.util.ArrayList<>()));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("TRB170626T13"), any())).thenReturn(false);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("TRB170626T13")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processBatch(List.of(d), LocalDateTime.now());

        verify(rateFetcher).fetchBatch(any());
        ArgumentCaptor<List<BondRateHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateHistoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
        assertThat(captor.getValue().get(0).getCouponRate()).isNull();
        assertThat(captor.getValue().get(0).getPrice()).isEqualByComparingTo("100");
    }

    @Test
    void processBatch_updatesExistingBond_andSavesFetchedRecords_forCouponBond() {
        BondSnapshotDto d = dto("S1", "ISIN1", new BigDecimal("10"), LocalDate.of(2020, 1, 1));
        Bond existing = bond("S1");
        existing.setCouponRate(new BigDecimal("10"));
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc("ISIN1"))
                .thenReturn(Optional.empty());
        when(rateFetcher.fetchBatch(any())).thenReturn(Map.of("ISIN1",
                new java.util.ArrayList<>(List.of(rateRecord("ISIN1", LocalDate.of(2020, 1, 2), new BigDecimal("10"))))));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("ISIN1"), any())).thenReturn(false);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processBatch(List.of(d), LocalDateTime.now());

        ArgumentCaptor<List<BondRateHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateHistoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
    }

    @Test
    void processBatch_skipsBatchFetch_whenNoHistoryAndMaturityStartNull() {
        BondSnapshotDto d = new BondSnapshotDto("S1", "ISIN1", new BigDecimal("100"),
                new BigDecimal("10"), null, LocalDate.of(2030, 1, 1), "Bond S1");
        Bond existing = bond("S1");
        existing.setCouponRate(new BigDecimal("10"));
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc("ISIN1"))
                .thenReturn(Optional.empty());
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processBatch(List.of(d), LocalDateTime.now());

        verify(rateFetcher, never()).fetchBatch(any());
    }

    @Test
    void processBatch_appendsTodaySnapshot_whenLastRateIsRecentAndCouponPresent() {
        LocalDate today = LocalDate.now();
        BondSnapshotDto d = dto("S1", "ISIN1", new BigDecimal("10"), LocalDate.of(2020, 1, 1));
        Bond existing = bond("S1");
        existing.setCouponRate(new BigDecimal("10"));
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc("ISIN1"))
                .thenReturn(Optional.of(rateRecord("ISIN1", today, new BigDecimal("10"))));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate("ISIN1", today)).thenReturn(false);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processBatch(List.of(d), LocalDateTime.now());

        verify(rateFetcher, never()).fetchBatch(any());
        verify(rateHistoryRepository).saveAll(any());
    }

    @Test
    void processBatch_skipsTodayAppend_whenRecordForTodayAlreadyExists() {
        LocalDate today = LocalDate.now();
        BondSnapshotDto d = dto("S1", "ISIN1", new BigDecimal("10"), LocalDate.of(2020, 1, 1));
        Bond existing = bond("S1");
        existing.setCouponRate(new BigDecimal("10"));
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc("ISIN1"))
                .thenReturn(Optional.of(rateRecord("ISIN1", today, new BigDecimal("10"))));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate("ISIN1", today)).thenReturn(true);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processBatch(List.of(d), LocalDateTime.now());

        verify(rateHistoryRepository, never()).saveAll(any());
    }

    @Test
    void processBatch_performsIncrementalFetch_whenLastRateOlderThanOneDay() {
        LocalDate today = LocalDate.now();
        BondSnapshotDto d = dto("S1", "ISIN1", new BigDecimal("10"), LocalDate.of(2020, 1, 1));
        Bond existing = bond("S1");
        existing.setCouponRate(new BigDecimal("10"));
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc("ISIN1"))
                .thenReturn(Optional.of(rateRecord("ISIN1", today.minusDays(10), new BigDecimal("10"))));
        when(rateFetcher.fetchBatch(any())).thenReturn(Map.of("ISIN1",
                new java.util.ArrayList<>(List.of(rateRecord("ISIN1", today.minusDays(5), new BigDecimal("10"))))));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("ISIN1"), any())).thenReturn(false);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processBatch(List.of(d), LocalDateTime.now());

        ArgumentCaptor<List<BondRateFetcher.BondHistoryTarget>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateFetcher).fetchBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        BondRateFetcher.BondHistoryTarget target = captor.getValue().get(0);
        assertThat(target.isinCode()).isEqualTo("ISIN1");
        assertThat(target.startDate()).isEqualTo(today.minusDays(9));
        assertThat(target.endDate()).isEqualTo(today);
    }

    @Test
    void processBatch_skipsTodayRecord_whenCouponRateIsNull() {
        BondSnapshotDto d = dto("S1", "ISIN1", null, LocalDate.of(2020, 1, 1));
        Bond existing = bond("S1");
        existing.setCouponRate(null);
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processBatch(List.of(d), LocalDateTime.now());

        verify(rateHistoryRepository, never()).saveAll(any());
    }

    @Test
    void processBatch_groupsMultipleBondsIntoSingleFetchCall() {
        BondSnapshotDto d1 = dto("S1", "ISIN1", new BigDecimal("10"), LocalDate.of(2020, 1, 1));
        BondSnapshotDto d2 = dto("S2", "ISIN2", new BigDecimal("12"), LocalDate.of(2021, 1, 1));
        Bond e1 = bond("S1"); e1.setCouponRate(new BigDecimal("10"));
        Bond e2 = bond("S2"); e2.setCouponRate(new BigDecimal("12"));
        when(bondRepository.findById("S1")).thenReturn(Optional.of(e1));
        when(bondRepository.findById("S2")).thenReturn(Optional.of(e2));
        when(assetRegistry.upsert(eq(MarketType.BOND), anyString())).thenReturn(bondAsset("X"));
        when(bondRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc(anyString())).thenReturn(Optional.empty());
        when(rateFetcher.fetchBatch(any())).thenReturn(Map.of("ISIN1", List.of(), "ISIN2", List.of()));
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc(anyString())).thenReturn(List.of());
        stubTransactionTemplate();

        service.processBatch(List.of(d1, d2), LocalDateTime.now());

        ArgumentCaptor<List<BondRateFetcher.BondHistoryTarget>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateFetcher).fetchBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).extracting(BondRateFetcher.BondHistoryTarget::isinCode)
                .containsExactly("ISIN1", "ISIN2");
    }
}
