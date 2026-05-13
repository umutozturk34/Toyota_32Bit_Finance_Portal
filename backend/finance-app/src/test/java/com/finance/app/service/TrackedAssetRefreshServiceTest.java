package com.finance.app.service;

import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.core.service.TrackedAssetDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackedAssetRefreshServiceTest {

    @Mock private TrackedAssetDataService cryptoService;
    @Mock private TrackedAssetDataService stockService;

    private TrackedAssetRefreshService service;

    @BeforeEach
    void setUp() {
        when(cryptoService.getAssetType()).thenReturn(TrackedAssetType.CRYPTO);
        when(stockService.getAssetType()).thenReturn(TrackedAssetType.STOCK);
        service = new TrackedAssetRefreshService(List.of(cryptoService, stockService));
    }

    @Test
    void validateAssetExists_delegatesToDataService_byType() {
        TrackedAssetUpsertCommand command = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.CRYPTO).assetCode("bitcoin").build();

        service.validateAssetExists(command);

        verify(cryptoService).validateExists(command);
        verify(stockService, never()).validateExists(command);
    }

    @Test
    void validateAssetExists_raises_whenNoDataServiceForType() {
        TrackedAssetUpsertCommand command = TrackedAssetUpsertCommand.builder()
                .assetType(TrackedAssetType.FOREX).assetCode("USD").build();

        assertThatThrownBy(() -> service.validateAssetExists(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshAsync_delegatesToService() {
        service.refreshAsync(TrackedAssetType.CRYPTO, "bitcoin");

        verify(cryptoService).refresh("bitcoin");
    }

    @Test
    void refreshAsync_swallowsExceptions_silently() {
        org.mockito.Mockito.doThrow(new RuntimeException("upstream down"))
                .when(cryptoService).refresh("bitcoin");

        service.refreshAsync(TrackedAssetType.CRYPTO, "bitcoin");
    }

    @Test
    void clearCacheAsync_delegatesToService() {
        service.clearCacheAsync(TrackedAssetType.STOCK, "AAPL");

        verify(stockService).clearCache("AAPL");
    }

    @Test
    void clearCacheAsync_swallowsExceptions_silently() {
        org.mockito.Mockito.doThrow(new RuntimeException("cache down"))
                .when(stockService).clearCache("AAPL");

        service.clearCacheAsync(TrackedAssetType.STOCK, "AAPL");
    }
}
