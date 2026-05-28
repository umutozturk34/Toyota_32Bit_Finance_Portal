package com.finance.market.core.service;

import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;

/**
 * Per-market bridge between the tracked-asset catalogue and that market's data pipeline:
 * validation, refresh, and cache eviction. Dispatched by {@link #getAssetType()}.
 */
public interface TrackedAssetDataService {

    /** Tracked-asset type this service handles. */
    TrackedAssetType getAssetType();

    /** Verifies the upstream source knows the asset before it is tracked. */
    void validateExists(TrackedAssetUpsertCommand command);

    void refresh(String code);

    void refreshAll();

    /** Evicts cached data for the asset (e.g. after delete/disable). */
    void clearCache(String code);
}
