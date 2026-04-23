package com.finance.backend.service.assetpricing;

import com.finance.backend.config.CommissionProperties;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.AssetPricingPort;
import com.finance.backend.service.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FundPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Fund, FundCandle> cacheService;
    private final CommissionProperties commissionProperties;

    public FundPricingStrategy(MarketCacheService<Fund, FundCandle> cacheService,
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
