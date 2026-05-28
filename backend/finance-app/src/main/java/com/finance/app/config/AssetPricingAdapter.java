package com.finance.app.config;

import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.service.assetpricing.AssetPricingStrategy;
import com.finance.shared.util.EnumDispatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Wires the cross-module {@link AssetPricingPort} to per-{@link MarketType} pricing strategies, dispatching
 * spot/exit price, metadata and bundle lookups by type. Unknown types and downstream failures degrade to a
 * null/empty fallback (logged) rather than propagating, so portfolio valuation stays resilient.
 */
@Log4j2
@Component
public class AssetPricingAdapter implements AssetPricingPort {

    private static final AssetMeta EMPTY_META = new AssetMeta(null, null);

    private final Map<MarketType, AssetPricingStrategy> strategies;

    public AssetPricingAdapter(List<AssetPricingStrategy> strategyList) {
        this.strategies = EnumDispatcher.from(MarketType.class, strategyList, AssetPricingStrategy::marketType);
    }

    @Override
    public BigDecimal getPriceTry(MarketType type, String assetCode) {
        return dispatch(type, assetCode, "price", null, strategy -> strategy.getPriceTry(assetCode));
    }

    @Override
    public BigDecimal getExitPriceTry(MarketType type, String assetCode) {
        return dispatch(type, assetCode, "exitPrice", null, strategy -> strategy.getExitPriceTry(assetCode));
    }

    @Override
    public AssetMeta getAssetMeta(MarketType type, String assetCode) {
        return dispatch(type, assetCode, "metadata", EMPTY_META, strategy -> strategy.getAssetMeta(assetCode));
    }

    @Override
    public PriceBundle getBundle(MarketType type, String assetCode) {
        return dispatch(type, assetCode, "bundle", new PriceBundle(null, EMPTY_META),
                strategy -> strategy.getBundle(assetCode));
    }

    @Override
    public PriceBundle getExitBundle(MarketType type, String assetCode) {
        AssetPricingStrategy strategy = strategies.get(type);
        if (strategy == null) {
            log.warn("Unknown asset type: {}", type);
            return new PriceBundle(null, EMPTY_META);
        }
        try {
            return new PriceBundle(strategy.getExitPriceTry(assetCode), strategy.getAssetMeta(assetCode));
        } catch (Exception e) {
            log.warn("Failed to get exitBundle for {}:{} - {}", type, assetCode, e.getMessage());
            return new PriceBundle(null, EMPTY_META);
        }
    }

    private <T> T dispatch(MarketType type, String assetCode, String label, T fallback,
                           Function<AssetPricingStrategy, T> call) {
        AssetPricingStrategy strategy = strategies.get(type);
        if (strategy == null) {
            log.warn("Unknown asset type: {}", type);
            return fallback;
        }
        try {
            return call.apply(strategy);
        } catch (Exception e) {
            log.warn("Failed to get {} for {}:{} - {}", label, type, assetCode, e.getMessage());
            return fallback;
        }
    }
}
