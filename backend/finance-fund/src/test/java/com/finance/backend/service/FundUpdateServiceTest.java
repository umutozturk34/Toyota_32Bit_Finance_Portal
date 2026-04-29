package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.config.FundProperties;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.FundType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundUpdateServiceTest {

    @Mock private TefasClient tefasClient;
    @Mock private FundMapper fundMapper;
    @Mock private FundRepository fundRepository;
    @Mock private FundCandleRepository fundCandleRepository;
    @Mock private MarketCacheService<Fund, FundCandle> fundCacheService;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private FundSnapshotProcessor snapshotProcessor;
    @Mock private FundEntityWriter entityWriter;
    @Mock private FundBulkFetchExecutor bulkFetchExecutor;
    @Mock private TransactionTemplate transactionTemplate;

    private FundUpdateService service;

    @BeforeEach
    void setUp() {
        FundProperties props = new FundProperties();
        AppProperties app = new AppProperties();
        app.setTimezone("Europe/Istanbul");
        service = new FundUpdateService(tefasClient, fundMapper, fundRepository,
                fundCandleRepository, fundCacheService, trackedAssetQueryService,
                snapshotProcessor, entityWriter, bulkFetchExecutor, transactionTemplate, app, props);
    }

    @Test
    void should_skipBulkFetch_when_noTrackedFundsForType() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND))
                .thenReturn(List.of());
        when(fundRepository.findAllById(List.of())).thenReturn(List.of());
        when(fundRepository.findByFundType(FundType.BYF)).thenReturn(List.of());

        service.refreshAll();

        verify(bulkFetchExecutor, never()).runWindows(any(), anyList(), any(), any());
        verify(snapshotProcessor).refreshAll();
    }

    @Test
    void should_collectByfFundsFromRepository_when_typeIsBYF() {
        Fund byf = fundWith("BLH", FundType.BYF);
        when(fundRepository.findByFundType(FundType.BYF)).thenReturn(List.of(byf));
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND))
                .thenReturn(List.of());
        when(fundRepository.findAllById(List.of())).thenReturn(List.of());

        service.refreshAll();

        verify(bulkFetchExecutor).runWindows(
                eq(FundType.BYF),
                anyList(),
                argThat(map -> map.containsKey("BLH")),
                any());
    }

    @Test
    void should_filterOutByfFundsFromYatTrackedList_when_collectingYatFunds() {
        Fund yatFund = fundWith("TI2", FundType.YAT);
        Fund byfFund = fundWith("BLH", FundType.BYF);
        when(fundRepository.findByFundType(FundType.BYF)).thenReturn(List.of(byfFund));
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND))
                .thenReturn(List.of("TI2", "BLH"));
        when(fundRepository.findAllById(List.of("TI2", "BLH")))
                .thenReturn(List.of(yatFund, byfFund));

        service.refreshAll();

        verify(bulkFetchExecutor).runWindows(
                eq(FundType.YAT),
                anyList(),
                argThat(map -> map.containsKey("TI2") && !map.containsKey("BLH")),
                any());
    }

    @Test
    void should_skipFetch_when_allFundsUpToDate() {
        Fund byf = fundWith("BLH", FundType.BYF);
        when(fundRepository.findByFundType(FundType.BYF)).thenReturn(List.of(byf));
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND))
                .thenReturn(List.of());
        when(fundRepository.findAllById(List.of())).thenReturn(List.of());
        LocalDateTime fiveYearsAgo = LocalDateTime.now().minusYears(5);
        LocalDateTime futureLatest = LocalDateTime.now().plusDays(1);
        when(fundCandleRepository.findCandleDateRangePerFund())
                .thenReturn(Collections.singletonList(new Object[]{"BLH", fiveYearsAgo, futureLatest}));

        service.refreshAll();

        verify(bulkFetchExecutor, never()).runWindows(any(), anyList(), any(), any());
    }

    @Test
    void should_delegateToSnapshotProcessor_when_existsCalled() {
        when(snapshotProcessor.exists("TI2")).thenReturn(true);

        boolean result = service.exists("TI2");

        org.assertj.core.api.Assertions.assertThat(result).isTrue();
        verify(snapshotProcessor).exists("TI2");
    }

    private Fund fundWith(String code, FundType type) {
        Fund f = new Fund();
        f.setFundCode(code);
        f.setFundType(type);
        f.setLastUpdated(LocalDateTime.now());
        return f;
    }
}
