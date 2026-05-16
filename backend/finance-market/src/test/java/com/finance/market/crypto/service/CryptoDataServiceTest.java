package com.finance.market.crypto.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.crypto.model.Crypto;
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
class CryptoDataServiceTest {

    @Mock private CryptoUpdateService updateService;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Crypto> cacheService = org.mockito.Mockito.mock(MarketCacheService.class);

    private CryptoDataService service;

    @BeforeEach
    void setUp() {
        service = new CryptoDataService(cacheService, updateService);
    }

    @Test
    void should_returnCryptoType_when_getAssetTypeCalled() {
        assertThat(service.getAssetType()).isEqualTo(TrackedAssetType.CRYPTO);
    }

    @Test
    void should_passValidation_when_cryptoExists() {
        TrackedAssetUpsertCommand cmd = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.CRYPTO).assetCode("bitcoin").binanceSymbol("BTCUSDT").build();
        when(updateService.exists("bitcoin", "BTCUSDT")).thenReturn(true);

        assertThatCode(() -> service.validateExists(cmd)).doesNotThrowAnyException();
    }

    @Test
    void should_throwBusinessException_when_cryptoMissing() {
        TrackedAssetUpsertCommand cmd = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.CRYPTO).assetCode("unknown").build();
        when(updateService.exists("unknown", null)).thenReturn(false);

        assertThatThrownBy(() -> service.validateExists(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.market.cryptoNotFound");
    }

    @Test
    void should_delegateRefreshToUpdateService_when_refreshCalled() {
        service.refresh("bitcoin");

        verify(updateService).refresh("bitcoin");
    }

    @Test
    void should_delegateRefreshAllToUpdateService_when_refreshAllCalled() {
        service.refreshAll();

        verify(updateService).refreshAll();
    }

    @Test
    void should_delegateClearCacheToHelper_when_clearCacheCalled() {
        service.clearCache("bitcoin");

        verify(cacheService).clearCache("bitcoin");
    }
}
