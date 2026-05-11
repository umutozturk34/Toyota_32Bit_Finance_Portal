package com.finance.market.stock.service.assetpricing;

import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.common.model.MarketType;
import com.finance.market.stock.model.Stock;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StockPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Stock> cacheService;

    public StockPricingStrategy(MarketCacheService<Stock> cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public MarketType marketType() {
        return MarketType.STOCK;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Stock stock = cacheService.getSnapshot(assetCode);
        if (stock == null) {
            return null;
        }
        return normalize(stock.getCurrentPrice());
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Stock stock = cacheService.getSnapshot(assetCode);
        if (stock == null) {
            return new AssetPricingPort.PriceBundle(null, EMPTY_META);
        }
        BigDecimal price = normalize(stock.getCurrentPrice());
        return new AssetPricingPort.PriceBundle(
                price,
                new AssetPricingPort.AssetMeta(stock.resolveDisplayName(), stock.getImage()));
    }
}
