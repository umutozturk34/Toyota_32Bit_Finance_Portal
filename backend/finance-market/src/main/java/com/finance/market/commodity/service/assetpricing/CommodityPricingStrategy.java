package com.finance.market.commodity.service.assetpricing;

import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.repository.CommodityCandleRepository;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Prices commodities in TRY from the cached snapshot (already TRY-denominated), falling back to the
 * latest candle close; no FX conversion is needed since commodities are stored in TRY.
 */
@Component
public class CommodityPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Commodity> cacheService;
    private final CommodityCandleRepository candleRepository;

    public CommodityPricingStrategy(MarketCacheService<Commodity> cacheService,
                                    CommodityCandleRepository candleRepository) {
        this.cacheService = cacheService;
        this.candleRepository = candleRepository;
    }

    @Override
    public MarketType marketType() {
        return MarketType.COMMODITY;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Commodity commodity = cacheService.getSnapshot(assetCode);
        BigDecimal current = commodity != null ? normalize(commodity.getCurrentPrice()) : null;
        if (current != null && current.signum() > 0) return current;
        return candleRepository.findFirstByCommodityCodeAndCloseGreaterThanOrderByCandleDateDesc(assetCode, BigDecimal.ZERO)
                .map(c -> normalize(c.getClose()))
                .orElse(current);
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Commodity commodity = cacheService.getSnapshot(assetCode);
        if (commodity == null) {
            return new AssetPricingPort.PriceBundle(null, EMPTY_META);
        }
        BigDecimal price = normalize(commodity.getCurrentPrice());
        if (price == null || price.signum() <= 0) {
            price = candleRepository.findFirstByCommodityCodeAndCloseGreaterThanOrderByCandleDateDesc(assetCode, BigDecimal.ZERO)
                    .map(c -> normalize(c.getClose()))
                    .orElse(price);
        }
        return new AssetPricingPort.PriceBundle(
                price,
                new AssetPricingPort.AssetMeta(commodity.resolveDisplayName(), commodity.getImage()));
    }
}
