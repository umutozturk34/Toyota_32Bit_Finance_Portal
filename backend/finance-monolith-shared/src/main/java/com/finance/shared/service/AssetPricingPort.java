package com.finance.shared.service;

import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public interface AssetPricingPort {

    BigDecimal getPriceTry(MarketType type, String assetCode);

    record AssetMeta(String name, String image) {}

    record AssetKey(MarketType type, String assetCode) {}

    record PriceBundle(BigDecimal price, AssetMeta meta) {}

    default AssetMeta getAssetMeta(MarketType type, String assetCode) {
        return new AssetMeta(null, null);
    }

    default PriceBundle getBundle(MarketType type, String assetCode) {
        return new PriceBundle(
                getPriceTry(type, assetCode),
                getAssetMeta(type, assetCode));
    }

    default Map<AssetKey, PriceBundle> getBundles(Collection<AssetKey> keys) {
        return keys.stream()
                .map(key -> Map.entry(key, java.util.Optional.ofNullable(getBundle(key.type(), key.assetCode()))))
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (a, b) -> a));
    }

    default Map<AssetKey, BigDecimal> getPricesTry(Collection<AssetKey> keys) {
        return keys.stream()
                .map(key -> Map.entry(key, java.util.Optional.ofNullable(getPriceTry(key.type(), key.assetCode()))))
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (a, b) -> a));
    }

}
