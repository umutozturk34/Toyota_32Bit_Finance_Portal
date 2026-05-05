package com.finance.stock.service.assetpricing;
import com.finance.common.service.assetpricing.BaseAssetPricingStrategy;


import com.finance.common.config.CommissionProperties;
import com.finance.common.model.MarketType;
import com.finance.stock.model.Stock;
import com.finance.stock.model.StockCandle;
import com.finance.stock.repository.StockRepository;
import com.finance.common.service.AssetPricingPort;
import com.finance.cache.service.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StockPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Stock> cacheService;
    private final CommissionProperties commissionProperties;
    private final StockRepository repository;

    public StockPricingStrategy(MarketCacheService<Stock> cacheService,
                                CommissionProperties commissionProperties,
                                StockRepository repository) {
        this.cacheService = cacheService;
        this.commissionProperties = commissionProperties;
        this.repository = repository;
    }

    @Override
    public Map<String, BigDecimal> getAllPricesTry() {
        return repository.findAll().stream()
                .filter(s -> s.getCurrentPrice() != null)
                .collect(Collectors.toUnmodifiableMap(Stock::getCode, Stock::getCurrentPrice, (a, b) -> a));
    }

    @Override
    public MarketType marketType() {
        return MarketType.STOCK;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Stock stock = cacheService.getSnapshot(assetCode);
        if (stock == null) {
            return null;
        }
        return normalize(stock.getCurrentPrice());
    }

    @Override
    public BigDecimal getSellPriceTry(String assetCode) {
        return applyCommission(getPriceTry(assetCode), commissionProperties.getStockRate());
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Stock stock = cacheService.getSnapshot(assetCode);
        if (stock == null) {
            return new AssetPricingPort.PriceBundle(null, null, EMPTY_META);
        }
        BigDecimal price = normalize(stock.getCurrentPrice());
        BigDecimal sellPrice = applyCommission(price, commissionProperties.getStockRate());
        return new AssetPricingPort.PriceBundle(
                price,
                sellPrice,
                new AssetPricingPort.AssetMeta(stock.resolveDisplayName(), stock.getImage()));
    }
}
