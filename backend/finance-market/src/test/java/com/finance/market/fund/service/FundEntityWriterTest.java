package com.finance.market.fund.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.Asset;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.core.service.TrackedAssetCommandService;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.fund.mapper.FundMapper;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundCandle;
import com.finance.market.fund.model.FundType;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundEntityWriterTest {

    @Mock private FundRepository fundRepository;
    @Mock private FundCandleRepository fundCandleRepository;
    @Mock private FundMapper fundMapper;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private TrackedAssetCommandService trackedAssetCommandService;
    @Mock private AssetRegistryService assetRegistry;

    private FundEntityWriter writer;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        FundProperties fundProperties = new FundProperties();
        writer = new FundEntityWriter(fundRepository, fundCandleRepository, fundMapper,
                trackedAssetQueryService, trackedAssetCommandService, assetRegistry,
                appProperties, fundProperties);
    }

    private TefasFundDto dto(String code, BigDecimal price) {
        return new TefasFundDto(code, "name " + code,
                LocalDateTime.of(2026, 5, 12, 0, 0),
                price, null, null, null, null);
    }

    private Fund fund(String code, BigDecimal price) {
        Fund f = new Fund();
        f.setFundCode(code);
        f.setPrice(price);
        return f;
    }

    @Test
    void saveSnapshot_updatesExistingFund_whenRepositoryHasEntry() {
        TefasFundDto d = dto("TI2", new BigDecimal("1.50"));
        Fund existing = fund("TI2", new BigDecimal("1.40"));
        when(fundRepository.findById("TI2")).thenReturn(Optional.of(existing));
        when(assetRegistry.upsert(MarketType.FUND, "TI2"))
                .thenReturn(Asset.builder().marketType(MarketType.FUND).assetCode("TI2").build());

        Fund saved = writer.saveSnapshot(d, FundType.YAT);

        verify(fundMapper).updateEntity(eq(existing), eq(d), eq(FundType.YAT), any(LocalDateTime.class));
        verify(fundMapper, never()).toEntity(any(), any(), any());
        verify(fundRepository).save(existing);
        assertThat(saved).isSameAs(existing);
    }

    @Test
    void saveSnapshot_createsNewFund_whenRepositoryEmpty() {
        TefasFundDto d = dto("TI2", new BigDecimal("1.50"));
        Fund created = fund("TI2", new BigDecimal("1.50"));
        when(fundRepository.findById("TI2")).thenReturn(Optional.empty());
        when(fundMapper.toEntity(eq(d), eq(FundType.YAT), any(LocalDateTime.class))).thenReturn(created);
        when(assetRegistry.upsert(MarketType.FUND, "TI2"))
                .thenReturn(Asset.builder().marketType(MarketType.FUND).assetCode("TI2").build());

        Fund saved = writer.saveSnapshot(d, FundType.YAT);

        verify(fundMapper, never()).updateEntity(any(), any(), any(), any());
        verify(fundRepository).save(created);
        assertThat(saved).isSameAs(created);
    }

    @Test
    void refreshChangePercent_returnsFalse_whenFundPriceMissing() {
        Fund f = fund("TI2", null);

        boolean result = writer.refreshChangePercent(f, LocalDateTime.now());

        assertThat(result).isFalse();
        verify(fundRepository, never()).save(any());
    }

    @Test
    void refreshChangePercent_returnsFalse_whenCurrentDateNull() {
        Fund f = fund("TI2", new BigDecimal("1.50"));

        boolean result = writer.refreshChangePercent(f, null);

        assertThat(result).isFalse();
    }

    @Test
    void refreshChangePercent_appliesNewPercent_andPersists_whenValueChanges() {
        Fund f = fund("TI2", new BigDecimal("1.50"));
        f.setChangePercent(new BigDecimal("1.00"));
        FundCandle previous = new FundCandle();
        previous.setPrice(new BigDecimal("1.40"));
        when(fundCandleRepository.findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(
                eq("TI2"), any(LocalDateTime.class))).thenReturn(Optional.of(previous));

        boolean result = writer.refreshChangePercent(f, LocalDateTime.now());

        assertThat(result).isTrue();
        verify(fundRepository).save(f);
    }

    @Test
    void refreshChangePercent_returnsFalse_whenPreviousPriceMatchesAndPercentUnchanged() {
        Fund f = fund("TI2", new BigDecimal("1.50"));
        f.setChangePercent(null);
        when(fundCandleRepository.findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(
                eq("TI2"), any(LocalDateTime.class))).thenReturn(Optional.empty());

        boolean result = writer.refreshChangePercent(f, LocalDateTime.now());

        assertThat(result).isFalse();
        verify(fundRepository, never()).save(any());
    }

    @Test
    void upsertCandleFromDto_updatesExistingCandle_whenFoundForDate() {
        Fund f = fund("TI2", new BigDecimal("1.50"));
        TefasFundDto d = dto("TI2", new BigDecimal("1.50"));
        FundCandle existing = new FundCandle();
        when(fundCandleRepository.findByFundCodeAndCandleDate("TI2", d.date()))
                .thenReturn(Optional.of(existing));

        writer.upsertCandleFromDto(f, FundType.YAT, d);

        verify(fundMapper).updateCandleEntity(existing, d);
        verify(fundCandleRepository).save(existing);
        verify(fundMapper, never()).toCandleEntity(any(), any(), any());
    }

    @Test
    void upsertCandleFromDto_createsNewCandle_whenAbsentForDate() {
        Fund f = fund("TI2", new BigDecimal("1.50"));
        TefasFundDto d = dto("TI2", new BigDecimal("1.50"));
        FundCandle created = new FundCandle();
        when(fundCandleRepository.findByFundCodeAndCandleDate("TI2", d.date()))
                .thenReturn(Optional.empty());
        when(fundMapper.toCandleEntity(d, f, FundType.YAT)).thenReturn(created);

        writer.upsertCandleFromDto(f, FundType.YAT, d);

        verify(fundCandleRepository).save(created);
        verify(fundMapper, never()).updateCandleEntity(any(), any());
    }

    @Test
    void ensureByfTracked_delegatesToCommandService_withConfiguredSortOrder() {
        writer.ensureByfTracked("BTC1", "TEFAS name");

        verify(trackedAssetCommandService).autoTrack(eq(TrackedAssetType.FUND),
                eq("BTC1"), eq("TEFAS name"), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void saveCandleBatch_persistsNewCandlesOnly_whenSomeAlreadyExist() {
        Fund f = fund("TI2", new BigDecimal("1.50"));
        TefasFundDto d = dto("TI2", new BigDecimal("1.50"));
        when(fundCandleRepository.findByFundCodeAndCandleDateIn(eq("TI2"), any()))
                .thenReturn(List.of());
        FundCandle newCandle = new FundCandle();
        when(fundMapper.toCandleEntity(d, f, FundType.YAT)).thenReturn(newCandle);

        int changed = writer.saveCandleBatch(f, FundType.YAT, List.of(d));

        verify(fundCandleRepository).saveAll(List.of(newCandle));
        assertThat(changed).isPositive();
    }
}
