package com.finance.market.crypto.service.assetpricing;

import com.finance.market.crypto.model.Crypto;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CryptoPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Crypto> cacheService;

    public CryptoPricingStrategy(MarketCacheService<Crypto> cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public MarketType marketType() {
        return MarketType.CRYPTO;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Crypto crypto = cacheService.getSnapshot(assetCode);
        if (crypto == null) {
            return null;
        }
        return normalize(crypto.getCurrentPriceTry());
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
        return new AssetPricingPort.PriceBundle(
                price,
                new AssetPricingPort.AssetMeta(crypto.resolveDisplayName(), crypto.getImage()));
    }
}
