package com.finance.market.commodity.service;
import com.finance.market.commodity.service.CommodityDataService;

import com.finance.market.core.cache.MarketCacheService;


import com.finance.common.exception.BusinessException;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommodityCandle;
import com.finance.common.model.TrackedAssetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommodityDataServiceTest {

    private CommodityUpdateService updateService;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Commodity> cacheService = mock(MarketCacheService.class);
    private PreciousMetalDerivativeCalculator derivativeCalculator;
    private CommodityDataService service;

    @BeforeEach
    void setUp() {
        updateService = mock(CommodityUpdateService.class);
        derivativeCalculator = mock(PreciousMetalDerivativeCalculator.class);
        service = new CommodityDataService(updateService, cacheService, derivativeCalculator);
    }

    @Test
    void getAssetTypeReturnsCommodity() {
        TrackedAssetType type = service.getAssetType();

        assertThat(type).isEqualTo(TrackedAssetType.COMMODITY);
    }

    @Test
    void validateExistsThrowsWhenApiReportsMissing() {
        when(updateService.exists("GC=F")).thenReturn(false);

        assertThatThrownBy(() -> service.validateExists("GC=F"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.market.commodityNotFound");
    }

    @Test
    void validateExistsPassesWhenApiConfirms() {
        when(updateService.exists("GC=F")).thenReturn(true);

        service.validateExists("GC=F");

        verify(updateService).exists("GC=F");
    }

    @Test
    void validateExistsSkipsApiCheckForKnownDerivative() {
        when(derivativeCalculator.isKnownDerivative("XAUTRYG")).thenReturn(true);

        service.validateExists("  xautryg  ");

        verify(updateService, never()).exists(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void refreshDelegatesToUpdateService() {
        service.refresh("GC=F");

        verify(updateService).refresh("GC=F");
    }

    @Test
    void clearCacheNormalizesAndDelegates() {
        service.clearCache("  gc=f  ");

        verify(cacheService).clearCache("GC=F");
    }

    @Test
    void clearCacheIgnoresBlankInput() {
        service.clearCache("   ");

        verify(cacheService, never()).clearCache(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void refreshAllDelegatesToUpdateService() {
        service.refreshAll();

        verify(updateService).refreshAll();
    }
}
