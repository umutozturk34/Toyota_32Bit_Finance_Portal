package com.finance.backend.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public interface AssetPricingPort {

    BigDecimal getPriceTry(String assetType, String assetCode);

    BigDecimal getSellPriceTry(String assetType, String assetCode);

    record AssetMeta(String name, String image) {}

    record AssetKey(String assetType, String assetCode) {}

    record PriceBundle(BigDecimal price, BigDecimal sellPrice, AssetMeta meta) {}

    default AssetMeta getAssetMeta(String assetType, String assetCode) {
        return new AssetMeta(null, null);
    }

    default PriceBundle getBundle(String assetType, String assetCode) {
        return new PriceBundle(
                getPriceTry(assetType, assetCode),
                getSellPriceTry(assetType, assetCode),
                getAssetMeta(assetType, assetCode));
    }

    default Map<AssetKey, PriceBundle> getBundles(Collection<AssetKey> keys) {
        Map<AssetKey, PriceBundle> result = new LinkedHashMap<>();
        for (AssetKey key : keys) {
            result.put(key, getBundle(key.assetType(), key.assetCode()));
        }
        return result;
    }
}
