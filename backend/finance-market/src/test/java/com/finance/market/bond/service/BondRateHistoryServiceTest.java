package com.finance.market.bond.service;

import com.finance.common.exception.BusinessException;
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
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void processSingleBond_persistsSnapshot_andSkipsRateHistory_forZeroCoupon() {
        BondSnapshotDto d = dto("S1", "ISIN1", BigDecimal.ZERO, LocalDate.of(2020, 1, 1));
        Bond entity = bond("S1");
        when(bondRepository.findById("S1")).thenReturn(Optional.empty());
        when(bondMapper.toEntity(eq(d), any())).thenReturn(entity);
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(entity)).thenReturn(entity);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processSingleBond(d, LocalDateTime.now());

        verify(rateHistoryRepository, never()).saveAll(any());
        verify(bondCacheService).putSnapshot("S1", entity);
    }

    @Test
    void processSingleBond_updatesExistingBond_andSavesRateRecords_forCouponBond() {
        BondSnapshotDto d = dto("S1", "ISIN1", new BigDecimal("10"), LocalDate.of(2020, 1, 1));
        Bond existing = bond("S1");
        existing.setCouponRate(new BigDecimal("10"));
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc("ISIN1"))
                .thenReturn(Optional.empty());
        when(rateFetcher.fetch(eq("ISIN1"), eq(existing), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new java.util.ArrayList<>(List.of(rateRecord("ISIN1", LocalDate.of(2020, 1, 2), new BigDecimal("10")))));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("ISIN1"), any())).thenReturn(false);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processSingleBond(d, LocalDateTime.now());

        ArgumentCaptor<List<BondRateHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateHistoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
    }

    @Test
    void processSingleBond_throws_whenNoHistoryAndMaturityStartNull() {
        BondSnapshotDto d = new BondSnapshotDto("S1", "ISIN1", new BigDecimal("100"),
                new BigDecimal("10"), null, LocalDate.of(2030, 1, 1), "Bond S1");
        Bond existing = bond("S1");
        existing.setCouponRate(new BigDecimal("10"));
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc("ISIN1"))
                .thenReturn(Optional.empty());
        stubTransactionTemplate();

        assertThatThrownBy(() -> service.processSingleBond(d, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maturityStart");
    }

    @Test
    void processSingleBond_appendsTodaySnapshot_whenLastRateIsRecentAndCouponPresent() {
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

        service.processSingleBond(d, LocalDateTime.now());

        verify(rateFetcher, never()).fetch(anyString(), any(), any(), any());
        verify(rateHistoryRepository).saveAll(any());
    }

    @Test
    void processSingleBond_skipsTodayAppend_whenRecordForTodayAlreadyExists() {
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

        service.processSingleBond(d, LocalDateTime.now());

        verify(rateHistoryRepository, never()).saveAll(any());
    }

    @Test
    void processSingleBond_performsIncrementalFetch_whenLastRateOlderThanOneDay() {
        LocalDate today = LocalDate.now();
        BondSnapshotDto d = dto("S1", "ISIN1", new BigDecimal("10"), LocalDate.of(2020, 1, 1));
        Bond existing = bond("S1");
        existing.setCouponRate(new BigDecimal("10"));
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc("ISIN1"))
                .thenReturn(Optional.of(rateRecord("ISIN1", today.minusDays(10), new BigDecimal("10"))));
        when(rateFetcher.fetch(eq("ISIN1"), eq(existing), eq(today.minusDays(9)), eq(today)))
                .thenReturn(new java.util.ArrayList<>(List.of(rateRecord("ISIN1", today.minusDays(5), new BigDecimal("10")))));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate("ISIN1", today)).thenReturn(false);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processSingleBond(d, LocalDateTime.now());

        verify(rateFetcher).fetch(eq("ISIN1"), eq(existing), eq(today.minusDays(9)), eq(today));
    }

    @Test
    void processSingleBond_skipsTodayRecord_whenCouponRateIsNull() {
        BondSnapshotDto d = dto("S1", "ISIN1", null, LocalDate.of(2020, 1, 1));
        Bond existing = bond("S1");
        existing.setCouponRate(null);
        when(bondRepository.findById("S1")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.BOND, "S1")).thenReturn(bondAsset("S1"));
        when(bondRepository.save(existing)).thenReturn(existing);
        when(rateHistoryRepository.findByIsinCodeOrderByRateDateAsc("ISIN1")).thenReturn(List.of());
        stubTransactionTemplate();

        service.processSingleBond(d, LocalDateTime.now());

        verify(rateHistoryRepository, never()).saveAll(any());
    }
}
