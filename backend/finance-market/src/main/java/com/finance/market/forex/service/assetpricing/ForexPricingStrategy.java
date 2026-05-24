package com.finance.market.forex.service.assetpricing;

import com.finance.market.forex.model.Forex;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ForexPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Forex> cacheService;
    private final ForexCandleRepository candleRepository;

    public ForexPricingStrategy(MarketCacheService<Forex> cacheService,
                                ForexCandleRepository candleRepository) {
        this.cacheService = cacheService;
        this.candleRepository = candleRepository;
    }

    @Override
    public MarketType marketType() {
        return MarketType.FOREX;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Forex forex = cacheService.getSnapshot(assetCode);
        BigDecimal current = forex != null ? normalize(forex.getSellingPrice()) : null;
        if (current != null && current.signum() > 0) return current;
        return candleRepository.findFirstByCurrencyCodeOrderByCandleDateDesc(assetCode)
                .map(c -> normalize(c.getSellingPrice()))
                .orElse(current);
    }

    @Override
    public BigDecimal getExitPriceTry(String assetCode) {
        Forex forex = cacheService.getSnapshot(assetCode);
        if (forex != null) {
            BigDecimal exit = forex.getBuyingPrice() != null ? forex.getBuyingPrice() : forex.getSellingPrice();
            BigDecimal normalized = normalize(exit);
            if (normalized != null && normalized.signum() > 0) return normalized;
        }
        return candleRepository.findFirstByCurrencyCodeOrderByCandleDateDesc(assetCode)
                .map(c -> normalize(c.getBuyingPrice() != null ? c.getBuyingPrice() : c.getSellingPrice()))
                .orElse(null);
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Forex forex = cacheService.getSnapshot(assetCode);
        if (forex == null) {
            return new AssetPricingPort.PriceBundle(null, EMPTY_META);
        }
        BigDecimal price = normalize(forex.getSellingPrice());
        if (price == null || price.signum() <= 0) {
            price = candleRepository.findFirstByCurrencyCodeOrderByCandleDateDesc(assetCode)
                    .map(c -> normalize(c.getSellingPrice()))
                    .orElse(price);
        }
        return new AssetPricingPort.PriceBundle(
                price,
                new AssetPricingPort.AssetMeta(forex.resolveDisplayName(), forex.getImage()));
    }
}
