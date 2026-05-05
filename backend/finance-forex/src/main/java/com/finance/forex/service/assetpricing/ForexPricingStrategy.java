package com.finance.forex.service.assetpricing;

import com.finance.forex.model.Forex;
import com.finance.forex.model.ForexCandle;
import com.finance.forex.repository.ForexRepository;
import com.finance.common.model.MarketType;
import com.finance.common.service.AssetPricingPort;
import com.finance.common.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.cache.service.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ForexPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Forex> cacheService;
    private final ForexRepository repository;

    public ForexPricingStrategy(MarketCacheService<Forex> cacheService,
                                ForexRepository repository) {
        this.cacheService = cacheService;
        this.repository = repository;
    }

    @Override
    public Map<String, BigDecimal> getAllPricesTry() {
        return repository.findAll().stream()
                .map(f -> {
                    BigDecimal p = f.getSellingPrice() != null ? f.getSellingPrice() : f.getCurrentPrice();
                    return p == null ? null : new java.util.AbstractMap.SimpleEntry<>(f.getCode(), p);
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue, (a, b) -> a));
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
