package com.finance.backend.service.assetpricing;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.service.AssetPricingPort;
import com.finance.backend.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.backend.service.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StockPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Stock, StockCandle> cacheService;
    private final AppProperties appProperties;

    public StockPricingStrategy(MarketCacheService<Stock, StockCandle> cacheService,
                                AppProperties appProperties) {
        this.cacheService = cacheService;
        this.appProperties = appProperties;
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
    public BigDecimal getSellPriceTry(String assetCode) {
        return applyCommission(getPriceTry(assetCode), appProperties.getCommission().getStockRate());
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Stock stock = cacheService.getSnapshot(assetCode);
        if (stock == null) {
            return new AssetPricingPort.PriceBundle(null, null, EMPTY_META);
        }
        BigDecimal price = normalize(stock.getCurrentPrice());
        BigDecimal sellPrice = applyCommission(price, appProperties.getCommission().getStockRate());
        return new AssetPricingPort.PriceBundle(
                price,
                sellPrice,
                new AssetPricingPort.AssetMeta(stock.resolveDisplayName(), stock.getImage()));
    }
}
