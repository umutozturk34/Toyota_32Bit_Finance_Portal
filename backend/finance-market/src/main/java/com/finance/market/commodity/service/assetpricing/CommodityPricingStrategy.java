package com.finance.market.commodity.service.assetpricing;

import com.finance.market.core.model.BaseAsset;
import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;


import com.finance.market.core.config.CommissionProperties;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommodityCandle;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CommodityPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Commodity> cacheService;
    private final CommissionProperties commissionProperties;

    public CommodityPricingStrategy(MarketCacheService<Commodity> cacheService,
                                    CommissionProperties commissionProperties) {
        this.cacheService = cacheService;
        this.commissionProperties = commissionProperties;
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
        return commissionProperties.getCommodityRate();
    }
}
