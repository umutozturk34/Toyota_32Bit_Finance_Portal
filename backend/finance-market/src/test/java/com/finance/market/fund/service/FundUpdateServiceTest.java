package com.finance.market.fund.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundUpdateServiceTest {

    @Mock private FundRepository fundRepository;
    @Mock private FundCandleRepository fundCandleRepository;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Fund> fundCacheService;
    @Mock private FundSnapshotProcessor snapshotProcessor;
    @Mock private FundEntityWriter entityWriter;
    @Mock private FundCandleBulkSyncService bulkSyncService;
    @Mock private FundCandleIncrementalRefreshService incrementalRefreshService;
    @Mock private FundDetailEnrichmentService detailEnrichmentService;

    private FundUpdateService service;

    @BeforeEach
    void setUp() {
        service = new FundUpdateService(fundRepository, fundCandleRepository, fundCacheService,
                snapshotProcessor, entityWriter, bulkSyncService, incrementalRefreshService,
                detailEnrichmentService);
    }

    @Test
    void getMarketType_returnsFund() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.FUND);
    }

    @Test
    void refreshAll_orchestratesSnapshotRefreshThenCandlesThenChangePercent() {
        when(fundCandleRepository.findCandleDateRangePerFund()).thenReturn(List.of());
        when(fundRepository.findAll()).thenReturn(List.of());

        service.refreshAll();

        verify(snapshotProcessor).refreshAll();
        verify(bulkSyncService).refreshAllCandles();
    }

    @Test
    void refreshAll_updatesCacheForFundsWithChangedPercent() {
        Fund fund = fundWithCode("TYH");
        LocalDateTime ts = LocalDateTime.now();
        List<Object[]> rangeRows = new java.util.ArrayList<>();
        rangeRows.add(new Object[]{"TYH", ts, ts});
        when(fundCandleRepository.findCandleDateRangePerFund()).thenReturn(rangeRows);
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(entityWriter.refreshChangePercent(fund, ts)).thenReturn(true);

        service.refreshAll();

        verify(fundCacheService).putSnapshot("TYH", fund);
    }

    @Test
    void refreshAll_skipsCacheUpdate_whenChangePercentNotUpdated() {
        Fund fund = fundWithCode("TYH");
        LocalDateTime ts = LocalDateTime.now();
        List<Object[]> rangeRows = new java.util.ArrayList<>();
        rangeRows.add(new Object[]{"TYH", ts, ts});
        when(fundCandleRepository.findCandleDateRangePerFund()).thenReturn(rangeRows);
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(entityWriter.refreshChangePercent(fund, ts)).thenReturn(false);

        service.refreshAll();

        verify(fundCacheService, never()).putSnapshot(any(), any());
    }

    @Test
    void refreshAll_skipsFundsWithoutCandleData() {
        Fund fund = fundWithCode("MISSING");
        when(fundCandleRepository.findCandleDateRangePerFund()).thenReturn(List.of());
        when(fundRepository.findAll()).thenReturn(List.of(fund));

        service.refreshAll();

        verify(entityWriter, never()).refreshChangePercent(any(), any());
    }

    @Test
    void refresh_delegatesToProcessorAndIncrementalRefresh() {
        service.refresh("TYH");

        verify(snapshotProcessor).refreshOne("TYH");
        verify(incrementalRefreshService).refresh("TYH");
    }

    @Test
    void exists_delegatesToProcessor() {
        when(snapshotProcessor.exists("TYH")).thenReturn(true);

        assertThat(service.exists("TYH")).isTrue();
    }

    private Fund fundWithCode(String code) {
        Fund f = Fund.builder().build();
        f.setFundCode(code);
        return f;
    }
}
