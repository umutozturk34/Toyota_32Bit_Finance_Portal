package com.finance.backend.service;

import com.finance.backend.model.MarketType;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public interface AssetPricingPort {

    BigDecimal getPriceTry(MarketType type, String assetCode);

    BigDecimal getSellPriceTry(MarketType type, String assetCode);

    record AssetMeta(String name, String image) {}

    record AssetKey(MarketType type, String assetCode) {}

    record PriceBundle(BigDecimal price, BigDecimal sellPrice, AssetMeta meta) {}

    default AssetMeta getAssetMeta(MarketType type, String assetCode) {
        return new AssetMeta(null, null);
    }

    default PriceBundle getBundle(MarketType type, String assetCode) {
        return new PriceBundle(
                getPriceTry(type, assetCode),
                getSellPriceTry(type, assetCode),
                getAssetMeta(type, assetCode));
    }

    default Map<AssetKey, PriceBundle> getBundles(Collection<AssetKey> keys) {
        Map<AssetKey, PriceBundle> result = new LinkedHashMap<>();
        for (AssetKey key : keys) {
            result.put(key, getBundle(key.type(), key.assetCode()));
        }
        return result;
    }

    default Map<AssetKey, BigDecimal> getPricesTry(Collection<AssetKey> keys) {
        Map<AssetKey, BigDecimal> result = new LinkedHashMap<>();
        for (AssetKey key : keys) {
            result.put(key, getPriceTry(key.type(), key.assetCode()));
        }
        return result;
    }
}
