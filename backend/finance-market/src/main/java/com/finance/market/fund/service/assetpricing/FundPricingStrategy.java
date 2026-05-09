package com.finance.market.fund.service.assetpricing;

import com.finance.market.core.model.BaseAsset;
import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;


import com.finance.market.core.config.CommissionProperties;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundCandle;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FundPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Fund> cacheService;
    private final CommissionProperties commissionProperties;

    public FundPricingStrategy(MarketCacheService<Fund> cacheService,
                               CommissionProperties commissionProperties) {
        this.cacheService = cacheService;
        this.commissionProperties = commissionProperties;
    }

    @Override
    public MarketType marketType() {
        return MarketType.FUND;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Fund fund = cacheService.getSnapshot(assetCode);
        if (fund == null) {
            return null;
        }
        return normalize(fund.getPrice());
    }

    @Override
    public BigDecimal getSellPriceTry(String assetCode) {
        return applyCommission(getPriceTry(assetCode), commissionProperties.getFundRate());
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Fund fund = cacheService.getSnapshot(assetCode);
        if (fund == null) {
            return new AssetPricingPort.PriceBundle(null, null, EMPTY_META);
        }
        BigDecimal price = normalize(fund.getPrice());
        BigDecimal sellPrice = applyCommission(price, commissionProperties.getFundRate());
        return new AssetPricingPort.PriceBundle(
                price,
                sellPrice,
                new AssetPricingPort.AssetMeta(fund.resolveDisplayName(), fund.getImage()));
    }
}
