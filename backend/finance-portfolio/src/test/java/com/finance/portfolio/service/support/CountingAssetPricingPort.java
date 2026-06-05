package com.finance.portfolio.service.support;

import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class CountingAssetPricingPort implements AssetPricingPort {

    private final Map<AssetKey, BigDecimal> prices = new HashMap<>();
    private final Map<AssetKey, AssetMeta> metas = new HashMap<>();

    private final AtomicInteger priceCalls = new AtomicInteger();
    private final AtomicInteger metaCalls = new AtomicInteger();
    private final AtomicInteger batchPricesCalls = new AtomicInteger();
    private final AtomicInteger batchBundlesCalls = new AtomicInteger();

    public void seedPrice(String type, String code, BigDecimal value) {
        prices.put(new AssetKey(MarketType.valueOf(type), code), value);
    }

    public void seedMeta(String type, String code, AssetMeta meta) {
        metas.put(new AssetKey(MarketType.valueOf(type), code), meta);
    }

    @Override
    public BigDecimal getPriceTry(MarketType type, String assetCode) {
        priceCalls.incrementAndGet();
        return prices.get(new AssetKey(type, assetCode));
    }

    @Override
    public BigDecimal getExitPriceTry(MarketType type, String assetCode) {
        return getPriceTry(type, assetCode);
    }

    @Override
    public AssetMeta getAssetMeta(MarketType type, String assetCode) {
        metaCalls.incrementAndGet();
        return metas.getOrDefault(new AssetKey(type, assetCode), new AssetMeta(null, null));
    }

    public Map<AssetKey, BigDecimal> getPricesTry(Collection<AssetKey> keys) {
        batchPricesCalls.incrementAndGet();
        Map<AssetKey, BigDecimal> result = new LinkedHashMap<>();
        for (AssetKey key : keys) {
            result.put(key, prices.get(key));
        }
        return result;
    }

    @Override
    public Map<AssetKey, BigDecimal> getExitPricesTry(Collection<AssetKey> keys) {
        return getPricesTry(keys);
    }

    public Map<AssetKey, PriceBundle> getBundles(Collection<AssetKey> keys) {
        batchBundlesCalls.incrementAndGet();
        Map<AssetKey, PriceBundle> result = new LinkedHashMap<>();
        for (AssetKey key : keys) {
            BigDecimal price = prices.get(key);
            AssetMeta meta = metas.getOrDefault(key, new AssetMeta(null, null));
            result.put(key, new PriceBundle(price, meta));
        }
        return result;
    }

    @Override
    public Map<AssetKey, PriceBundle> getExitBundles(Collection<AssetKey> keys) {
        return getBundles(keys);
    }

    public int priceCalls() {
        return priceCalls.get();
    }

    public int metaCalls() {
        return metaCalls.get();
    }

    public int batchPricesCalls() {
        return batchPricesCalls.get();
    }

    public int batchBundlesCalls() {
        return batchBundlesCalls.get();
    }
}
