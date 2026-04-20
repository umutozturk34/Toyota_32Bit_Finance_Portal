package com.finance.backend.service;

import com.finance.backend.dto.request.BulkTrackedAssetOrderUpdateRequest;
import com.finance.backend.dto.request.UpsertTrackedAssetRequest;
import com.finance.backend.dto.response.TrackedAssetResponse;
import com.finance.backend.mapper.TrackedAssetMapper;
import com.finance.backend.model.TrackedAssetType;
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
        TrackedAssetResponse previous = trackedAssetQueryService
            .getTrackedAsset(request.getAssetType(), request.getAssetCode())
            .orElse(null);

        boolean isNewOrReEnabled = previous == null
                || (!previous.isEnabled() && (request.getEnabled() == null || request.getEnabled()));
        if (isNewOrReEnabled) {
            trackedAssetRefreshService.validateAssetExists(request.getAssetType(), request.getAssetCode());
        }

        TrackedAssetResponse data = trackedAssetCommandService.upsert(trackedAssetMapper.toUpsertCommand(request));

        boolean shouldRefresh = shouldRefreshAfterUpsert(previous, data);
        boolean shouldClear = !data.isEnabled();
        TrackedAssetType type = data.getAssetType();
        String code = data.getAssetCode();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (shouldRefresh) {
                    trackedAssetRefreshService.refreshAsync(type, code);
                } else if (shouldClear) {
                    trackedAssetRefreshService.clearCacheAsync(type, code);
                }
                refreshDefaultPage(type);
            }
        });

        return data;
    }

    private boolean shouldRefreshAfterUpsert(TrackedAssetResponse previous, TrackedAssetResponse current) {
        if (!current.isEnabled()) {
            return false;
        }
        if (previous == null || !previous.isEnabled()) {
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
    public void setEnabled(TrackedAssetType type, String code, boolean enabled) {
        trackedAssetCommandService.setEnabled(type, code, enabled);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (enabled) {
                    trackedAssetRefreshService.refreshAsync(type, code);
                } else {
                    trackedAssetRefreshService.clearCacheAsync(type, code);
                }
                refreshDefaultPage(type);
            }
        });
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
