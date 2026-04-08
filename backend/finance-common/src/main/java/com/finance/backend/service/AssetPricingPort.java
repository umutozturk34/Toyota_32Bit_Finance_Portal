package com.finance.backend.service;

import java.math.BigDecimal;

public interface AssetPricingPort {

    BigDecimal getPriceTry(String assetType, String assetCode);

    record AssetMeta(String name, String image) {}

    default AssetMeta getAssetMeta(String assetType, String assetCode) {
        return new AssetMeta(null, null);
    }
}
