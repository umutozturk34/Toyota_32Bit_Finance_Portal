package com.finance.backend.service;

import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.TrackedAssetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommodityDataServiceTest {

    private CommoditySnapshotService snapshotService;
    private CommodityCandleService candleService;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Commodity, CommodityCandle> cacheService = mock(MarketCacheService.class);
    private CommodityDataService service;

    @BeforeEach
    void setUp() {
        snapshotService = mock(CommoditySnapshotService.class);
        candleService = mock(CommodityCandleService.class);
        service = new CommodityDataService(snapshotService, candleService, cacheService);
    }

    @Test
    void getAssetTypeReturnsCommodity() {
        assertThat(service.getAssetType()).isEqualTo(TrackedAssetType.COMMODITY);
    }

    @Test
    void validateExistsThrowsWhenApiReportsMissing() {
        when(snapshotService.existsInApi("GC=F")).thenReturn(false);

        assertThatThrownBy(() -> service.validateExists("GC=F"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Emtia bulunamadı");
    }

    @Test
    void validateExistsPassesWhenApiConfirms() {
        when(snapshotService.existsInApi("GC=F")).thenReturn(true);

        service.validateExists("GC=F");

        verify(snapshotService).existsInApi("GC=F");
    }

    @Test
    void refreshSnapshotDelegatesToSnapshotService() {
        service.refreshSnapshot("GC=F");

        verify(snapshotService).refreshTrackedCommoditySnapshot("GC=F");
    }

    @Test
    void refreshCandlesDelegatesToCandleService() {
        service.refreshCandles("GC=F");

        verify(candleService).refreshTrackedCommodityCandles("GC=F");
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
    void updateCommoditySnapshotsDelegates() {
        service.updateCommoditySnapshots();

        verify(snapshotService).refreshAll();
    }

    @Test
    void updateCommodityCandlesDelegates() {
        service.updateCommodityCandles();

        verify(candleService).refreshAll();
    }
}
