package com.finance.backend.service;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.mapper.CommodityResponseMapper;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommodityQueryServiceTest {

    @SuppressWarnings("unchecked")
    private final MarketCacheService<Commodity, CommodityCandle> cacheService = mock(MarketCacheService.class);
    private CommodityCandleRepository candleRepository;
    private CommodityResponseMapper responseMapper;
    private TrackedAssetQueryService trackedAssetQueryService;
    private CommodityQueryService service;

    @BeforeEach
    void setUp() {
        candleRepository = mock(CommodityCandleRepository.class);
        responseMapper = mock(CommodityResponseMapper.class);
        trackedAssetQueryService = mock(TrackedAssetQueryService.class);
        service = new CommodityQueryService(cacheService, candleRepository, responseMapper, trackedAssetQueryService);
    }

    @Test
    void getMarketTypeReturnsCommodity() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.COMMODITY);
    }

    @Test
    void getHistoryAllUsesCacheHistory() {
        when(trackedAssetQueryService.resolveEnabledCodeOrThrow(TrackedAssetType.COMMODITY, "GC=F"))
                .thenReturn("GC=F");
        when(cacheService.getHistory("GC=F")).thenReturn(List.of());
        when(responseMapper.toCommodityCandleResponses(anyList())).thenReturn(List.of());

        List<CandleResponse> result = (List<CandleResponse>) service.getHistory("GC=F", CandlePeriod.ALL);

        assertThat(result).isNotNull();
        verify(cacheService).getHistory("GC=F");
        verify(candleRepository, never()).findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                any(), any(), any());
    }

    @Test
    void getHistoryWithPeriodQueriesRepository() {
        when(trackedAssetQueryService.resolveEnabledCodeOrThrow(TrackedAssetType.COMMODITY, "GC=F"))
                .thenReturn("GC=F");
        when(candleRepository.findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any())).thenReturn(List.of());
        when(responseMapper.toCommodityCandleResponses(anyList())).thenReturn(List.of());

        service.getHistory("GC=F", CandlePeriod.ONE_MONTH);

        verify(candleRepository).findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any());
        verify(cacheService, never()).getHistory(any());
    }
}
