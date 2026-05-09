package com.finance.market.commodity.service;
import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.market.commodity.mapper.CommodityResponseMapper;
import com.finance.shared.model.CandlePeriod;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.commodity.repository.CommodityCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommodityQueryServiceTest {

    private CommodityCandleRepository candleRepository;
    private CommodityResponseMapper responseMapper;
    private TrackedAssetQueryService trackedAssetQueryService;
    private CommodityQueryService service;

    @BeforeEach
    void setUp() {
        candleRepository = mock(CommodityCandleRepository.class);
        responseMapper = mock(CommodityResponseMapper.class);
        trackedAssetQueryService = mock(TrackedAssetQueryService.class);
        service = new CommodityQueryService(candleRepository, responseMapper, trackedAssetQueryService);
    }

    @Test
    void getMarketTypeReturnsCommodity() {
        MarketType type = service.getMarketType();

        assertThat(type).isEqualTo(MarketType.COMMODITY);
    }

    @Test
    void getHistoryAllQueriesRepositoryFromEpoch() {
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.COMMODITY, "GC=F"))
                .thenReturn("GC=F");
        when(candleRepository.findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any())).thenReturn(List.of());
        when(responseMapper.toCommodityCandleResponses(anyList())).thenReturn(List.of());

        service.getHistory("GC=F", CandlePeriod.ALL);

        verify(candleRepository).findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any());
    }

    @Test
    void getHistoryWithPeriodQueriesRepository() {
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.COMMODITY, "GC=F"))
                .thenReturn("GC=F");
        when(candleRepository.findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any())).thenReturn(List.of());
        when(responseMapper.toCommodityCandleResponses(anyList())).thenReturn(List.of());

        service.getHistory("GC=F", CandlePeriod.ONE_MONTH);

        verify(candleRepository).findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any());
    }
}
