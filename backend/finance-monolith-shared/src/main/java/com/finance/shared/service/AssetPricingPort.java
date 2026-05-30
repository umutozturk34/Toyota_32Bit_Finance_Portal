package com.finance.shared.service;

import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cross-module pricing seam: lets feature modules (e.g. portfolio) value assets in TRY without
 * depending on the market module directly. Implemented by the application-layer adapter that fans
 * out to per-market pricing services; only {@link #getPriceTry} is required, all bundle/batch
 * variants derive from it. Prices are current spot values resolved per {@link MarketType}.
 */
public interface AssetPricingPort {

    /** Current TRY price for the asset, or {@code null} when unknown/unavailable. */
    BigDecimal getPriceTry(MarketType type, String assetCode);

    /** Price used when closing/exiting a position; defaults to the spot price unless overridden. */
    default BigDecimal getExitPriceTry(MarketType type, String assetCode) {
        return getPriceTry(type, assetCode);
    }

    /** Display metadata (name, image) for an asset. */
    record AssetMeta(String name, String image) {}

    /** Identity of a priced asset, used as the batch lookup key. */
    record AssetKey(MarketType type, String assetCode) {}

    /** A resolved price paired with its display metadata. */
    record PriceBundle(BigDecimal price, AssetMeta meta) {}

    /** Display metadata for an asset; defaults to empty when the adapter has none. */
    default AssetMeta getAssetMeta(MarketType type, String assetCode) {
        return new AssetMeta(null, null);
    }

    default PriceBundle getBundle(MarketType type, String assetCode) {
        return new PriceBundle(
                getPriceTry(type, assetCode),
                getAssetMeta(type, assetCode));
    }

    default PriceBundle getExitBundle(MarketType type, String assetCode) {
        return new PriceBundle(
                getExitPriceTry(type, assetCode),
                getAssetMeta(type, assetCode));
    }

    /** Batch spot bundles keyed by asset; entries with no resolvable price are omitted. */
    default Map<AssetKey, PriceBundle> getBundles(Collection<AssetKey> keys) {
        return keys.stream()
                .map(key -> Map.entry(key, java.util.Optional.ofNullable(getBundle(key.type(), key.assetCode()))))
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (a, b) -> a));
    }

    /** Batch exit-price bundles keyed by asset; entries with no resolvable price are omitted. */
    default Map<AssetKey, PriceBundle> getExitBundles(Collection<AssetKey> keys) {
        return keys.stream()
                .map(key -> Map.entry(key, java.util.Optional.ofNullable(getExitBundle(key.type(), key.assetCode()))))
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (a, b) -> a));
    }

    /** Batch spot TRY prices keyed by asset; entries with no resolvable price are omitted. */
    default Map<AssetKey, BigDecimal> getPricesTry(Collection<AssetKey> keys) {
        return keys.stream()
                .map(key -> Map.entry(key, java.util.Optional.ofNullable(getPriceTry(key.type(), key.assetCode()))))
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (a, b) -> a));
    }

    /** Batch exit TRY prices keyed by asset; entries with no resolvable price are omitted. */
    default Map<AssetKey, BigDecimal> getExitPricesTry(Collection<AssetKey> keys) {
        return keys.stream()
                .map(key -> Map.entry(key, java.util.Optional.ofNullable(getExitPriceTry(key.type(), key.assetCode()))))
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (a, b) -> a));
    }

}
