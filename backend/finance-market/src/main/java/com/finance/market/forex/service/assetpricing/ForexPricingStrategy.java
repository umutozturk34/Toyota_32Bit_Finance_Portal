package com.finance.market.forex.service.assetpricing;

import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.common.model.MarketType;
import com.finance.common.service.AssetPricingPort;
import com.finance.common.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.cache.service.MarketCacheService;
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
        BigDecimal basePrice = forex.getSellingPrice() != null ? forex.getSellingPrice() : forex.getCurrentPrice();
        return normalize(basePrice);
    }

    @Override
    public BigDecimal getSellPriceTry(String assetCode) {
        Forex forex = cacheService.getSnapshot(assetCode);
        if (forex == null) {
            return null;
        }
        return normalize(forex.getCurrentPrice());
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Forex forex = cacheService.getSnapshot(assetCode);
        if (forex == null) {
            return new AssetPricingPort.PriceBundle(null, null, EMPTY_META);
        }
        BigDecimal price = normalize(forex.getSellingPrice() != null ? forex.getSellingPrice() : forex.getCurrentPrice());
        BigDecimal sellPrice = normalize(forex.getCurrentPrice());
        return new AssetPricingPort.PriceBundle(
                price,
                sellPrice,
                new AssetPricingPort.AssetMeta(forex.resolveDisplayName(), forex.getImage()));
    }
}
