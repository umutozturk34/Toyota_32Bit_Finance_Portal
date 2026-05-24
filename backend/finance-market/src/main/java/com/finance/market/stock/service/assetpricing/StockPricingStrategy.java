package com.finance.market.stock.service.assetpricing;

import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.common.model.MarketType;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.repository.StockCandleRepository;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StockPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Stock> cacheService;
    private final StockCandleRepository candleRepository;

    public StockPricingStrategy(MarketCacheService<Stock> cacheService,
                                StockCandleRepository candleRepository) {
        this.cacheService = cacheService;
        this.candleRepository = candleRepository;
    }

    @Override
    public MarketType marketType() {
        return MarketType.STOCK;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Stock stock = cacheService.getSnapshot(assetCode);
        BigDecimal current = stock != null ? normalize(stock.getCurrentPrice()) : null;
        if (current != null && current.signum() > 0) return current;
        return candleRepository.findFirstByStockSymbolOrderByCandleDateDesc(assetCode)
                .map(c -> normalize(c.getClose()))
                .orElse(current);
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Stock stock = cacheService.getSnapshot(assetCode);
        if (stock == null) {
            return new AssetPricingPort.PriceBundle(null, EMPTY_META);
        }
        BigDecimal price = normalize(stock.getCurrentPrice());
        if (price == null || price.signum() <= 0) {
            price = candleRepository.findFirstByStockSymbolOrderByCandleDateDesc(assetCode)
                    .map(c -> normalize(c.getClose()))
                    .orElse(price);
        }
        return new AssetPricingPort.PriceBundle(
                price,
                new AssetPricingPort.AssetMeta(stock.resolveDisplayName(), stock.getImage()));
    }
}
