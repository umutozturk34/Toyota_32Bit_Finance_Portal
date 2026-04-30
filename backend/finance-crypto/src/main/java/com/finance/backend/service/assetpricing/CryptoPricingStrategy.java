package com.finance.backend.service.assetpricing;

import com.finance.backend.config.CommissionProperties;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.AssetPricingPort;
import com.finance.backend.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.backend.service.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CryptoPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Crypto> cacheService;
    private final CommissionProperties commissionProperties;

    public CryptoPricingStrategy(MarketCacheService<Crypto> cacheService,
                                 CommissionProperties commissionProperties) {
        this.cacheService = cacheService;
        this.commissionProperties = commissionProperties;
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
    public BigDecimal getSellPriceTry(String assetCode) {
        return applyCommission(getPriceTry(assetCode), commissionProperties.getCryptoRate());
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
            return new AssetPricingPort.PriceBundle(null, null, EMPTY_META);
        }
        BigDecimal price = normalize(crypto.getCurrentPriceTry());
        BigDecimal sellPrice = applyCommission(price, commissionProperties.getCryptoRate());
        return new AssetPricingPort.PriceBundle(
                price,
                sellPrice,
                new AssetPricingPort.AssetMeta(crypto.resolveDisplayName(), crypto.getImage()));
    }
}
