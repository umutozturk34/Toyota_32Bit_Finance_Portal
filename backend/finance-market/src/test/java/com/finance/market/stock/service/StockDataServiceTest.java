package com.finance.market.stock.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.stock.model.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDataServiceTest {

    @Mock private StockUpdateService updateService;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Stock> cacheService = org.mockito.Mockito.mock(MarketCacheService.class);

    private StockDataService service;

    @BeforeEach
    void setUp() {
        service = new StockDataService(cacheService, updateService);
    }

    @Test
    void should_returnStockType_when_getAssetTypeCalled() {
        assertThat(service.getAssetType()).isEqualTo(TrackedAssetType.STOCK);
    }

    @Test
    void should_passValidation_when_stockExists() {
        TrackedAssetUpsertCommand cmd = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.STOCK).assetCode("AKBNK.IS").build();
        when(updateService.exists("AKBNK.IS")).thenReturn(true);

        assertThatCode(() -> service.validateExists(cmd)).doesNotThrowAnyException();
    }

    @Test
    void should_throwBusinessException_when_stockMissing() {
        TrackedAssetUpsertCommand cmd = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.STOCK).assetCode("ZZZ.IS").build();
        when(updateService.exists("ZZZ.IS")).thenReturn(false);

        assertThatThrownBy(() -> service.validateExists(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.market.stockNotFound");
    }

    @Test
    void should_delegateRefreshToUpdateService_when_refreshCalled() {
        service.refresh("AKBNK.IS");

        verify(updateService).refresh("AKBNK.IS");
    }

    @Test
    void should_delegateRefreshAllToUpdateService_when_refreshAllCalled() {
        service.refreshAll();

        verify(updateService).refreshAll();
    }

    @Test
    void should_delegateClearCacheToHelper_when_clearCacheCalled() {
        service.clearCache("AKBNK.IS");

        verify(cacheService).clearCache("AKBNK.IS");
    }
}
