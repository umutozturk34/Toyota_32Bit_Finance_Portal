package com.finance.backend.service;

import com.finance.backend.model.TrackedAssetType;

public interface TrackedAssetDataService {

    TrackedAssetType getAssetType();

    void validateExists(String code);

    void refreshSnapshot(String code);

    void refreshCandles(String code);

    void refreshAllSnapshots();

    void refreshAllCandles();

    void clearCache(String code);
}
