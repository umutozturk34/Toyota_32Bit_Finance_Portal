package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.config.FundProperties;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.exception.ExternalApiRequestException;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.FundType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundSnapshotServiceTest {

    @Mock private TefasClient tefasClient;
    @Mock private FundMapper fundMapper;
    @Mock private FundRepository fundRepository;
    @Mock private MarketCacheService<Fund, FundCandle> fundCacheService;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private TrackedAssetCommandService trackedAssetCommandService;
    @Mock private FundChangeCalculator fundChangeCalculator;
    @Mock private TransactionTemplate transactionTemplate;

    private FundSnapshotService service;

    @BeforeEach
    void setUp() {
        FundProperties props = new FundProperties();
        service = new FundSnapshotService(tefasClient, fundMapper, fundRepository,
                fundCacheService, trackedAssetQueryService, trackedAssetCommandService,
                fundChangeCalculator, transactionTemplate, props, "Europe/Istanbul");
    }

    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<Fund>>getArgument(0).doInTransaction(null));
    }

    @Test
    void should_persistOnlyTrackedYatFunds_when_bulkContainsExtraneousCodes() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND))
                .thenReturn(List.of("TI2"));
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any())).thenReturn(List.of());
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any())).thenReturn(List.of(
                dtoFor("TI2"),
                dtoFor("EXTRA")));
        when(fundRepository.findById("TI2")).thenReturn(Optional.empty());
        Fund mapped = fundWith("TI2");
        when(fundMapper.toEntity(any(), eq(FundType.YAT), any(LocalDateTime.class))).thenReturn(mapped);
        when(fundChangeCalculator.calculateChangePercent(eq("TI2"), any())).thenReturn(BigDecimal.ZERO);
        when(fundRepository.save(mapped)).thenReturn(mapped);
        stubTransactionTemplate();

        service.refreshAll();

        verify(fundRepository).save(mapped);
        verify(fundCacheService).putSnapshot("TI2", mapped);
        verify(fundCacheService, never()).putSnapshot(eq("EXTRA"), any());
    }

    @Test
    void should_autoTrackBYFFunds_when_byfBulkFetchReturnsResults() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND))
                .thenReturn(List.of());
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any()))
                .thenReturn(List.of(dtoFor("BLH")));
        when(tefasClient.bulkFetch(eq(FundType.YAT), any(), any())).thenReturn(List.of());
        when(fundRepository.findById("BLH")).thenReturn(Optional.empty());
        Fund mapped = fundWith("BLH");
        when(fundMapper.toEntity(any(), eq(FundType.BYF), any(LocalDateTime.class))).thenReturn(mapped);
        when(fundChangeCalculator.calculateChangePercent(eq("BLH"), any())).thenReturn(BigDecimal.ZERO);
        when(fundRepository.save(mapped)).thenReturn(mapped);
        when(trackedAssetQueryService.getTrackedAsset(TrackedAssetType.FUND, "BLH"))
                .thenReturn(Optional.empty());
        stubTransactionTemplate();

        service.refreshAll();

        verify(trackedAssetCommandService, atLeastOnce()).upsert(any());
    }

    @Test
    void should_propagateException_when_wafBlocksBulkRequest() {
        when(tefasClient.bulkFetch(eq(FundType.BYF), any(), any()))
                .thenThrow(new ExternalApiRequestException("TEFAS", "WAF blocked"));

        assertThatThrownBy(() -> service.refreshAll())
                .isInstanceOf(ExternalApiRequestException.class);
    }

    private Fund fundWith(String code) {
        Fund f = new Fund();
        f.setFundCode(code);
        f.setName("name " + code);
        return f;
    }

    private TefasFundDto dtoFor(String code) {
        return new TefasFundDto(code, "name " + code,
                LocalDate.now().atStartOfDay(),
                new BigDecimal("1.00"), null, null, null, null);
    }
}
