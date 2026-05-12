package com.finance.market.core.service;

import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;

public interface TrackedAssetDataService {

    TrackedAssetType getAssetType();

    void validateExists(TrackedAssetUpsertCommand command);

    void refresh(String code);

    void refreshAll();

    void clearCache(String code);
}
