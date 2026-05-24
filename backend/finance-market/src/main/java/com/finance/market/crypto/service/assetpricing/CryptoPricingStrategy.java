package com.finance.market.crypto.service.assetpricing;

import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.repository.CryptoCandleRepository;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CryptoPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Crypto> cacheService;
    private final CryptoCandleRepository candleRepository;

    public CryptoPricingStrategy(MarketCacheService<Crypto> cacheService,
                                 CryptoCandleRepository candleRepository) {
        this.cacheService = cacheService;
        this.candleRepository = candleRepository;
    }

    @Override
    public MarketType marketType() {
        return MarketType.CRYPTO;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Crypto crypto = cacheService.getSnapshot(assetCode);
        BigDecimal current = crypto != null ? normalize(crypto.getCurrentPriceTry()) : null;
        if (current != null && current.signum() > 0) return current;
        return candleRepository.findFirstByCryptoIdOrderByCandleDateDesc(assetCode)
                .map(c -> normalize(c.getClose()))
                .orElse(current);
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        Crypto crypto = cacheService.getSnapshot(assetCode);
        if (crypto == null) {
            return EMPTY_META;
        }
        return new AssetPricingPort.AssetMeta(crypto.resolveDisplayName(), crypto.getImage());
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Crypto crypto = cacheService.getSnapshot(assetCode);
        if (crypto == null) {
            return new AssetPricingPort.PriceBundle(null, EMPTY_META);
        }
        BigDecimal price = normalize(crypto.getCurrentPriceTry());
        if (price == null || price.signum() <= 0) {
            price = candleRepository.findFirstByCryptoIdOrderByCandleDateDesc(assetCode)
                    .map(c -> normalize(c.getClose()))
                    .orElse(price);
        }
        return new AssetPricingPort.PriceBundle(
                price,
                new AssetPricingPort.AssetMeta(crypto.resolveDisplayName(), crypto.getImage()));
    }
}
