package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.CommodityResponseMapper;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommodityMarketAssetProviderTest {

    private CommodityRepository commodityRepository;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService = mock(MarketCacheService.class);
    private CommodityResponseMapper commodityResponseMapper;
    private TrackedAssetQueryService trackedAssetQueryService;
    private CommodityMarketAssetProvider provider;

    @BeforeEach
    void setUp() {
        commodityRepository = mock(CommodityRepository.class);
        commodityResponseMapper = mock(CommodityResponseMapper.class);
        trackedAssetQueryService = mock(TrackedAssetQueryService.class);
        provider = new CommodityMarketAssetProvider(
                commodityRepository,
                commodityCacheService,
                commodityResponseMapper,
                trackedAssetQueryService);
    }

    @Test
    void getTypeReturnsCommodity() {
        assertThat(provider.getType()).isEqualTo(MarketType.COMMODITY);
    }

    @Test
    void getByCodeReturnsNullWhenSnapshotMissing() {
        when(commodityCacheService.getSnapshot("GC=F")).thenReturn(null);

        assertThat(provider.getByCode("GC=F")).isNull();
    }

    @Test
    void getByCodeMapsSnapshotToResponse() {
        Commodity commodity = buildCommodity("GC=F");
        MarketAssetResponse expected = new MarketAssetResponse(
                "GC=F", "Altın", null, MarketType.COMMODITY,
                new BigDecimal("160000.5000"), null, null, null, null);
        when(commodityCacheService.getSnapshot("GC=F")).thenReturn(commodity);
        when(commodityResponseMapper.toMarketAssetResponses(anyList())).thenReturn(List.of(expected));
        when(trackedAssetQueryService.getEnabledDisplayNameMap(TrackedAssetType.COMMODITY))
                .thenReturn(java.util.Map.of());

        MarketAssetResponse response = provider.getByCode("GC=F");

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("GC=F");
        verify(commodityCacheService).getSnapshot("GC=F");
        verify(commodityResponseMapper).toMarketAssetResponses(anyList());
    }

    private Commodity buildCommodity(String code) {
        Commodity commodity = new Commodity();
        commodity.setCommodityCode(code);
        commodity.setCurrentPrice(new BigDecimal("160000.5000"));
        return commodity;
    }
}
