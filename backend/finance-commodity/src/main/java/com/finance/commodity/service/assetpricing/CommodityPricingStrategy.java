package com.finance.commodity.service.assetpricing;
import com.finance.common.service.assetpricing.BaseAssetPricingStrategy;


import com.finance.common.config.CommissionProperties;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.commodity.model.Commodity;
import com.finance.commodity.model.CommodityCandle;
import com.finance.commodity.repository.CommodityRepository;
import com.finance.common.model.MarketType;
import com.finance.common.service.AssetPricingPort;
import com.finance.cache.service.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CommodityPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Commodity> cacheService;
    private final CommissionProperties commissionProperties;
    private final CommodityRepository repository;

    public CommodityPricingStrategy(MarketCacheService<Commodity> cacheService,
                                    CommissionProperties commissionProperties,
                                    CommodityRepository repository) {
        this.cacheService = cacheService;
        this.commissionProperties = commissionProperties;
        this.repository = repository;
    }

    @Override
    public Map<String, BigDecimal> getAllPricesTry() {
        return repository.findAll().stream()
                .filter(c -> c.getCurrentPrice() != null)
                .collect(Collectors.toUnmodifiableMap(Commodity::getCode, Commodity::getCurrentPrice, (a, b) -> a));
    }

    @Override
    public Map<String, AssetSnapshot> getAllSnapshots() {
        return repository.findAll().stream()
                .filter(c -> c.getCurrentPrice() != null)
                .map(Commodity::toSnapshot)
                .collect(Collectors.toUnmodifiableMap(AssetSnapshot::code, s -> s, (a, b) -> a));
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
