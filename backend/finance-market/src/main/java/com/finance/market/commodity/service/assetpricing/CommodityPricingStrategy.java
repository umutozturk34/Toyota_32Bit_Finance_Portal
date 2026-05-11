package com.finance.market.commodity.service.assetpricing;

import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.market.commodity.model.Commodity;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CommodityPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Commodity> cacheService;

    public CommodityPricingStrategy(MarketCacheService<Commodity> cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public MarketType marketType() {
        return MarketType.COMMODITY;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Commodity commodity = cacheService.getSnapshot(assetCode);
        if (commodity == null) return null;
        return normalize(commodity.getCurrentPrice());
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Commodity commodity = cacheService.getSnapshot(assetCode);
        if (commodity == null) {
            return new AssetPricingPort.PriceBundle(null, EMPTY_META);
        }
        BigDecimal price = normalize(commodity.getCurrentPrice());
        return new AssetPricingPort.PriceBundle(
                price,
                new AssetPricingPort.AssetMeta(commodity.resolveDisplayName(), commodity.getImage()));
    }
}
