package com.finance.backend.config;

import com.finance.backend.model.MarketType;
import com.finance.backend.service.AssetPricingPort;
import com.finance.backend.service.assetpricing.AssetPricingStrategy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Log4j2
@Component
public class AssetPricingAdapter implements AssetPricingPort {

    private static final AssetMeta EMPTY_META = new AssetMeta(null, null);

    private final Map<MarketType, AssetPricingStrategy> strategies;

    public AssetPricingAdapter(List<AssetPricingStrategy> strategyList) {
        this.strategies = new EnumMap<>(MarketType.class);
        strategyList.forEach(strategy -> this.strategies.put(strategy.marketType(), strategy));
    }

    @Override
    public BigDecimal getPriceTry(MarketType type, String assetCode) {
        return dispatch(type, assetCode, "price", null, strategy -> strategy.getPriceTry(assetCode));
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
