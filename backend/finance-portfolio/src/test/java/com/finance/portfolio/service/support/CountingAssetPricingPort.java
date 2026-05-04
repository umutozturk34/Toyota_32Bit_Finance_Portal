package com.finance.portfolio.service.support;

import com.finance.common.model.MarketType;
import com.finance.common.service.AssetPricingPort;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class CountingAssetPricingPort implements AssetPricingPort {

    private final Map<AssetKey, BigDecimal> prices = new HashMap<>();
    private final Map<AssetKey, BigDecimal> sellPrices = new HashMap<>();
    private final Map<AssetKey, AssetMeta> metas = new HashMap<>();

    private final AtomicInteger priceCalls = new AtomicInteger();
    private final AtomicInteger sellPriceCalls = new AtomicInteger();
    private final AtomicInteger metaCalls = new AtomicInteger();
    private final AtomicInteger batchPricesCalls = new AtomicInteger();
    private final AtomicInteger batchBundlesCalls = new AtomicInteger();

    public void seedPrice(String type, String code, BigDecimal value) {
        prices.put(new AssetKey(MarketType.valueOf(type), code), value);
    }

    public void seedSellPrice(String type, String code, BigDecimal value) {
        sellPrices.put(new AssetKey(MarketType.valueOf(type), code), value);
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
    public BigDecimal getSellPriceTry(MarketType type, String assetCode) {
        sellPriceCalls.incrementAndGet();
        return sellPrices.get(new AssetKey(type, assetCode));
    }

    @Override
    public AssetMeta getAssetMeta(MarketType type, String assetCode) {
        metaCalls.incrementAndGet();
        return metas.getOrDefault(new AssetKey(type, assetCode), new AssetMeta(null, null));
    }

    @Override
    public Map<AssetKey, BigDecimal> getPricesTry(Collection<AssetKey> keys) {
        batchPricesCalls.incrementAndGet();
        Map<AssetKey, BigDecimal> result = new LinkedHashMap<>();
        for (AssetKey key : keys) {
            result.put(key, prices.get(key));
        }
        return result;
    }

    @Override
    public Map<AssetKey, PriceBundle> getBundles(Collection<AssetKey> keys) {
        batchBundlesCalls.incrementAndGet();
        Map<AssetKey, PriceBundle> result = new LinkedHashMap<>();
        for (AssetKey key : keys) {
            BigDecimal price = prices.get(key);
            BigDecimal sell = sellPrices.get(key);
            AssetMeta meta = metas.getOrDefault(key, new AssetMeta(null, null));
            result.put(key, new PriceBundle(price, sell, meta));
        }
        return result;
    }

    public int priceCalls() {
        return priceCalls.get();
    }

    public int sellPriceCalls() {
        return sellPriceCalls.get();
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
