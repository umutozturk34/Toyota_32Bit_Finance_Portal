package com.finance.app.service;

import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.core.dto.request.BulkTrackedAssetOrderUpdateRequest;
import com.finance.market.core.dto.request.TrackedAssetOrderItemRequest;
import com.finance.market.core.dto.request.UpsertTrackedAssetRequest;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.market.core.mapper.TrackedAssetMapper;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.market.core.service.TrackedAssetCommandService;
import com.finance.market.core.service.TrackedAssetQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackedAssetAdminServiceTest {

    @Mock private TrackedAssetQueryService queryService;
    @Mock private TrackedAssetCommandService commandService;
    @Mock private TrackedAssetMapper mapper;
    @Mock private TrackedAssetRefreshService refreshService;
    @Mock private MarketUpdatePort marketUpdatePort;

    private TrackedAssetAdminService service;

    @BeforeEach
    void setUp() {
        service = new TrackedAssetAdminService(queryService, commandService, mapper,
                refreshService, Optional.of(marketUpdatePort));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private TrackedAssetResponse response(TrackedAssetType type, String code, String binance) {
        return TrackedAssetResponse.builder()
                .assetType(type).assetCode(code).binanceSymbol(binance).build();
    }

    private TrackedAssetUpsertCommand command(TrackedAssetType type, String code) {
        return TrackedAssetUpsertCommand.builder()
                .assetType(type).assetCode(code).build();
    }

    private void runAfterCommit() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
    }

    @Test
    void upsert_validatesNewAsset_andRefreshesAsync_afterCommit() {
        UpsertTrackedAssetRequest req = new UpsertTrackedAssetRequest();
        TrackedAssetUpsertCommand cmd = command(TrackedAssetType.CRYPTO, "bitcoin");
        when(mapper.toUpsertCommand(req)).thenReturn(cmd);
        when(queryService.getTrackedAsset(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.empty());
        TrackedAssetResponse saved = response(TrackedAssetType.CRYPTO, "bitcoin", "BTCUSDT");
        when(commandService.upsert(cmd)).thenReturn(saved);

        service.upsert(req);
        runAfterCommit();

        verify(refreshService).validateAssetExists(cmd);
        verify(refreshService).refreshAsync(TrackedAssetType.CRYPTO, "bitcoin");
        verify(marketUpdatePort).onMarketDataUpdated(TrackedAssetType.CRYPTO.marketType());
    }

    @Test
    void upsert_skipsValidation_whenAssetAlreadyTracked() {
        UpsertTrackedAssetRequest req = new UpsertTrackedAssetRequest();
        TrackedAssetUpsertCommand cmd = command(TrackedAssetType.STOCK, "AAPL");
        TrackedAssetResponse existing = response(TrackedAssetType.STOCK, "AAPL", null);
        TrackedAssetResponse updated = response(TrackedAssetType.STOCK, "AAPL", null);
        when(mapper.toUpsertCommand(req)).thenReturn(cmd);
        when(queryService.getTrackedAsset(TrackedAssetType.STOCK, "AAPL"))
                .thenReturn(Optional.of(existing));
        when(commandService.upsert(cmd)).thenReturn(updated);

        service.upsert(req);
        runAfterCommit();

        verify(refreshService, never()).validateAssetExists(any());
        verify(refreshService, never()).refreshAsync(any(), anyString());
    }

    @Test
    void upsert_refreshesCryptoSymbolChange_whenBinanceSymbolUpdated() {
        UpsertTrackedAssetRequest req = new UpsertTrackedAssetRequest();
        TrackedAssetUpsertCommand cmd = command(TrackedAssetType.CRYPTO, "bitcoin");
        TrackedAssetResponse existing = response(TrackedAssetType.CRYPTO, "bitcoin", "BTCUSDT");
        TrackedAssetResponse updated = response(TrackedAssetType.CRYPTO, "bitcoin", "BTCUSD");
        when(mapper.toUpsertCommand(req)).thenReturn(cmd);
        when(queryService.getTrackedAsset(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.of(existing));
        when(commandService.upsert(cmd)).thenReturn(updated);

        service.upsert(req);
        runAfterCommit();

        verify(refreshService).refreshAsync(TrackedAssetType.CRYPTO, "bitcoin");
    }

    @Test
    void upsert_skipsRefresh_whenCryptoBinanceSymbolUnchanged() {
        UpsertTrackedAssetRequest req = new UpsertTrackedAssetRequest();
        TrackedAssetUpsertCommand cmd = command(TrackedAssetType.CRYPTO, "bitcoin");
        TrackedAssetResponse existing = response(TrackedAssetType.CRYPTO, "bitcoin", "BTCUSDT");
        TrackedAssetResponse updated = response(TrackedAssetType.CRYPTO, "bitcoin", "BTCUSDT");
        when(mapper.toUpsertCommand(req)).thenReturn(cmd);
        when(queryService.getTrackedAsset(TrackedAssetType.CRYPTO, "bitcoin"))
                .thenReturn(Optional.of(existing));
        when(commandService.upsert(cmd)).thenReturn(updated);

        service.upsert(req);
        runAfterCommit();

        verify(refreshService, never()).refreshAsync(any(), anyString());
    }

    @Test
    void delete_clearsCacheAsync_andTriggersMarketDataUpdate_afterCommit() {
        service.delete(TrackedAssetType.STOCK, "AAPL");
        runAfterCommit();

        verify(commandService).delete(TrackedAssetType.STOCK, "AAPL");
        verify(refreshService).clearCacheAsync(TrackedAssetType.STOCK, "AAPL");
        verify(marketUpdatePort).onMarketDataUpdated(TrackedAssetType.STOCK.marketType());
    }

    @Test
    void updateSortOrders_invokesCommandService_andRefreshesPage_afterCommit() {
        BulkTrackedAssetOrderUpdateRequest req = new BulkTrackedAssetOrderUpdateRequest();
        req.setAssetType(TrackedAssetType.STOCK);
        req.setItems(List.of(new TrackedAssetOrderItemRequest("AAPL", 1)));

        service.updateSortOrders(req);
        runAfterCommit();

        verify(commandService).updateSortOrders(eq(TrackedAssetType.STOCK), eq(req.getItems()));
        verify(marketUpdatePort).onMarketDataUpdated(TrackedAssetType.STOCK.marketType());
    }
}
