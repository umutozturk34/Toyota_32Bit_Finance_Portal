package com.finance.app.config;

import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.common.service.AssetPricingPort;
import com.finance.common.service.assetpricing.AssetPricingStrategy;
import com.finance.common.util.EnumDispatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
    public Map<String, BigDecimal> getAllPricesTry(MarketType type) {
        AssetPricingStrategy strategy = strategies.get(type);
        if (strategy == null) {
            log.warn("Unknown asset type: {}", type);
            return Map.of();
        }
        try {
            return strategy.getAllPricesTry();
        } catch (Exception e) {
            log.warn("Failed to get all prices for {} - {}", type, e.getMessage());
            return Map.of();
        }
    }

    @Override
    public Map<String, AssetSnapshot> getAllSnapshots(MarketType type) {
        AssetPricingStrategy strategy = strategies.get(type);
        if (strategy == null) {
            log.warn("Unknown asset type: {}", type);
            return Map.of();
        }
        try {
            return strategy.getAllSnapshots();
        } catch (Exception e) {
            log.warn("Failed to get all snapshots for {} - {}", type, e.getMessage());
            return Map.of();
        }
    }

    @Override
    public BigDecimal getSellPriceTry(MarketType type, String assetCode) {
        return dispatch(type, assetCode, "sell price", null, strategy -> strategy.getSellPriceTry(assetCode));
    }

    @Override
    public AssetMeta getAssetMeta(MarketType type, String assetCode) {
        return dispatch(type, assetCode, "metadata", EMPTY_META, strategy -> strategy.getAssetMeta(assetCode));
    }

    @Override
    public PriceBundle getBundle(MarketType type, String assetCode) {
        return dispatch(type, assetCode, "bundle", new PriceBundle(null, null, EMPTY_META),
                strategy -> strategy.getBundle(assetCode));
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
