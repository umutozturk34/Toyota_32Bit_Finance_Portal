package com.finance.backend.service;

import com.finance.backend.model.TrackedAssetType;

public interface TrackedAssetDataService {

    TrackedAssetType getAssetType();

    void validateExists(String code);

    void refresh(String code);

    void refreshAll();

    void clearCache(String code);
}
