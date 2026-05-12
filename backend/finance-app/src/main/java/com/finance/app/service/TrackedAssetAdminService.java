package com.finance.app.service;
import com.finance.market.core.service.MarketUpdatePort;

import com.finance.market.core.service.TrackedAssetCommandService;

import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.core.dto.request.BulkTrackedAssetOrderUpdateRequest;
import com.finance.market.core.dto.request.UpsertTrackedAssetRequest;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.market.core.mapper.TrackedAssetMapper;
import com.finance.common.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

@Service
@Log4j2
@RequiredArgsConstructor
public class TrackedAssetAdminService {

    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TrackedAssetCommandService trackedAssetCommandService;
    private final TrackedAssetMapper trackedAssetMapper;
    private final TrackedAssetRefreshService trackedAssetRefreshService;
    private final Optional<MarketUpdatePort> marketUpdatePort;

    @Transactional
    public TrackedAssetResponse upsert(UpsertTrackedAssetRequest request) {
        TrackedAssetUpsertCommand command = trackedAssetMapper.toUpsertCommand(request);
        TrackedAssetResponse previous = trackedAssetQueryService
            .getTrackedAsset(command.getAssetType(), command.getAssetCode())
            .orElse(null);

        if (previous == null) {
            trackedAssetRefreshService.validateAssetExists(command);
        }

        TrackedAssetResponse data = trackedAssetCommandService.upsert(command);

        boolean shouldRefresh = shouldRefreshAfterUpsert(previous, data);
        TrackedAssetType type = data.getAssetType();
        String code = data.getAssetCode();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (shouldRefresh) {
                    trackedAssetRefreshService.refreshAsync(type, code);
                }
                refreshDefaultPage(type);
            }
        });

        return data;
    }

    private boolean shouldRefreshAfterUpsert(TrackedAssetResponse previous, TrackedAssetResponse current) {
        if (previous == null) {
            return true;
        }
        if (current.getAssetType() == TrackedAssetType.CRYPTO) {
            String prevSymbol = previous.getBinanceSymbol();
            String currSymbol = current.getBinanceSymbol();
            return prevSymbol == null ? currSymbol != null : !prevSymbol.equals(currSymbol);
        }
        return false;
    }

    @Transactional
    public void delete(TrackedAssetType type, String code) {
        trackedAssetCommandService.delete(type, code);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                trackedAssetRefreshService.clearCacheAsync(type, code);
                refreshDefaultPage(type);
            }
        });
    }

    @Transactional
    public void updateSortOrders(BulkTrackedAssetOrderUpdateRequest request) {
        trackedAssetCommandService.updateSortOrders(request.getAssetType(), request.getItems());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshDefaultPage(request.getAssetType());
            }
        });
    }

    private void refreshDefaultPage(TrackedAssetType type) {
        marketUpdatePort.ifPresent(port -> port.onMarketDataUpdated(type.marketType()));
    }
}
