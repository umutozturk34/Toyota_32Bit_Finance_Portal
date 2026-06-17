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

/**
 * Admin commands for the tracked-asset registry (upsert/delete/reorder/enable-disable). Validates new
 * assets exist before persisting, and schedules side effects (data refresh, cache clear, default-page
 * refresh) only {@code afterCommit} so they never run against an uncommitted or rolled-back change.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class TrackedAssetAdminService {

    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TrackedAssetCommandService trackedAssetCommandService;
    private final TrackedAssetMapper trackedAssetMapper;
    private final TrackedAssetRefreshService trackedAssetRefreshService;
    private final Optional<MarketUpdatePort> marketUpdatePort;

    /**
     * Creates or updates a tracked asset. A brand-new asset is first validated to exist at its source; the
     * data refresh and default-page refresh run only {@code afterCommit} (see {@link #shouldRefreshAfterUpsert}
     * for when a refetch is warranted).
     */
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

    /**
     * Refresh is needed for a brand-new asset, or for crypto whose Binance symbol changed (so price data
     * re-syncs); existing non-crypto edits don't warrant a data refetch.
     */
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

    /**
     * Hard-removes the tracked asset, then {@code afterCommit} evicts its cache and refreshes the type's
     * default page. An auto-discovered asset may reappear on the next discovery sweep (use {@link #setEnabled}
     * to hide one permanently).
     */
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

    /** Bulk-reorders the type's tracked assets, refreshing the default page {@code afterCommit} so the new order shows. */
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

    /**
     * Soft enable/disable of a tracked asset. Refreshes the type's default page {@code afterCommit} so the
     * asset appears in (enable) or drops out of (disable) the public listings without a delete — and without
     * being resurrected by the next discovery sweep.
     */
    @Transactional
    public void setEnabled(TrackedAssetType type, String code, boolean enabled) {
        trackedAssetCommandService.setEnabled(type, code, enabled);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshDefaultPage(type);
            }
        });
    }

    private void refreshDefaultPage(TrackedAssetType type) {
        marketUpdatePort.ifPresent(port -> port.onMarketDataUpdated(type.marketType()));
    }
}
