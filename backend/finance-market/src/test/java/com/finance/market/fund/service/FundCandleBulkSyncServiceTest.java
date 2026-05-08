package com.finance.market.fund.service;
import com.finance.common.service.TrackedAssetQueryService;


import com.finance.common.config.AppProperties;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class FundCandleBulkSyncServiceTest {

    @Mock private FundRepository fundRepository;
    @Mock private FundCandleRepository fundCandleRepository;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private FundEntityWriter entityWriter;
    @Mock private FundBulkFetchExecutor bulkFetchExecutor;

    private FundCandleBulkSyncService service;

    @BeforeEach
    void setUp() {
        FundProperties props = new FundProperties();
        AppProperties app = new AppProperties();
        app.setTimezone("Europe/Istanbul");
        service = new FundCandleBulkSyncService(fundRepository, fundCandleRepository,
                trackedAssetQueryService, entityWriter, bulkFetchExecutor, app, props);
    }

    @Test
    void should_skipBulkFetch_when_noTrackedFundsForType() {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND))
                .thenReturn(List.of());
        when(fundRepository.findAllById(List.of())).thenReturn(List.of());
        when(fundRepository.findByFundType(FundType.BYF)).thenReturn(List.of());

        service.refreshAllCandles();

        verify(bulkFetchExecutor, never()).runWindows(any(), anyList(), any(), any());
    }

    @Test
    void should_collectByfFundsFromRepository_when_typeIsBYF() {
        Fund byf = fundWith("BLH", FundType.BYF);
        when(fundRepository.findByFundType(FundType.BYF)).thenReturn(List.of(byf));
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND))
                .thenReturn(List.of());
        when(fundRepository.findAllById(List.of())).thenReturn(List.of());

        service.refreshAllCandles();

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

        service.refreshAllCandles();

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
        when(fundCandleRepository.countCandlesPerFund())
                .thenReturn(Collections.singletonList(new Object[]{"BLH", 1000L}));

        service.refreshAllCandles();

        verify(bulkFetchExecutor, never()).runWindows(any(), anyList(), any(), any());
    }

    private Fund fundWith(String code, FundType type) {
        Fund f = new Fund();
        f.setFundCode(code);
        f.setFundType(type);
        f.setLastUpdated(LocalDateTime.now());
        return f;
    }
}
