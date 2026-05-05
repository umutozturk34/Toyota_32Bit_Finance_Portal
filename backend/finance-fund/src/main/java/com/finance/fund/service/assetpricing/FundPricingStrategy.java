package com.finance.fund.service.assetpricing;
import com.finance.common.service.assetpricing.BaseAssetPricingStrategy;


import com.finance.common.config.CommissionProperties;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.fund.model.Fund;
import com.finance.fund.model.FundCandle;
import com.finance.fund.repository.FundRepository;
import com.finance.common.model.MarketType;
import com.finance.common.service.AssetPricingPort;
import com.finance.cache.service.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FundPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Fund> cacheService;
    private final CommissionProperties commissionProperties;
    private final FundRepository repository;

    public FundPricingStrategy(MarketCacheService<Fund> cacheService,
                               CommissionProperties commissionProperties,
                               FundRepository repository) {
        this.cacheService = cacheService;
        this.commissionProperties = commissionProperties;
        this.repository = repository;
    }

    @Override
    public Map<String, BigDecimal> getAllPricesTry() {
        return repository.findAll().stream()
                .filter(f -> f.getPrice() != null)
                .collect(Collectors.toUnmodifiableMap(Fund::getCode, Fund::getPrice, (a, b) -> a));
    }

    @Override
    public Map<String, AssetSnapshot> getAllSnapshots() {
        return repository.findAll().stream()
                .filter(f -> f.getPrice() != null)
                .map(Fund::toSnapshot)
                .collect(Collectors.toUnmodifiableMap(AssetSnapshot::code, s -> s, (a, b) -> a));
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
