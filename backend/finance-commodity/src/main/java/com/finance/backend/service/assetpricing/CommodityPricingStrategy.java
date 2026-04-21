package com.finance.backend.service.assetpricing;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.AssetPricingPort;
import com.finance.backend.service.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CommodityPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Commodity, CommodityCandle> cacheService;
    private final AppProperties appProperties;

    public CommodityPricingStrategy(MarketCacheService<Commodity, CommodityCandle> cacheService,
                                    AppProperties appProperties) {
        this.cacheService = cacheService;
        this.appProperties = appProperties;
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
    public BigDecimal getSellPriceTry(String assetCode) {
        return applyCommission(getPriceTry(assetCode), commodityCommissionRate());
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Commodity commodity = cacheService.getSnapshot(assetCode);
        if (commodity == null) {
            return new AssetPricingPort.PriceBundle(null, null, EMPTY_META);
        }
        BigDecimal price = normalize(commodity.getCurrentPrice());
        BigDecimal sellPrice = applyCommission(price, commodityCommissionRate());
        return new AssetPricingPort.PriceBundle(
                price,
                sellPrice,
                new AssetPricingPort.AssetMeta(commodity.resolveDisplayName(), commodity.getImage()));
    }

    private BigDecimal commodityCommissionRate() {
        return appProperties.getCommission().getCommodityRate();
    }
}
