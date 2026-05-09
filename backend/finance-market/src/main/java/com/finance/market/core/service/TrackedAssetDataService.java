package com.finance.market.core.service;

import com.finance.common.model.TrackedAssetType;

public interface TrackedAssetDataService {

    TrackedAssetType getAssetType();

    void validateExists(String code);

    void refresh(String code);

    void refreshAll();

    void clearCache(String code);
}
