package com.finance.market.forex.service.assetpricing;

import com.finance.market.forex.model.Forex;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ForexPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Forex> cacheService;

    public ForexPricingStrategy(MarketCacheService<Forex> cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public MarketType marketType() {
        return MarketType.FOREX;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Forex forex = cacheService.getSnapshot(assetCode);
        if (forex == null) {
            return null;
        }
        return normalize(forex.getSellingPrice());
    }

    @Override
    public BigDecimal getExitPriceTry(String assetCode) {
        Forex forex = cacheService.getSnapshot(assetCode);
        if (forex == null) {
            return null;
        }
        BigDecimal exit = forex.getBuyingPrice() != null ? forex.getBuyingPrice() : forex.getSellingPrice();
        return normalize(exit);
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Forex forex = cacheService.getSnapshot(assetCode);
        if (forex == null) {
            return new AssetPricingPort.PriceBundle(null, EMPTY_META);
        }
        return new AssetPricingPort.PriceBundle(
                normalize(forex.getSellingPrice()),
                new AssetPricingPort.AssetMeta(forex.resolveDisplayName(), forex.getImage()));
    }
}
