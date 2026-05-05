package com.finance.crypto.service.assetpricing;

import com.finance.common.config.CommissionProperties;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.crypto.model.Crypto;
import com.finance.crypto.model.CryptoCandle;
import com.finance.crypto.repository.CryptoRepository;
import com.finance.common.model.MarketType;
import com.finance.common.service.AssetPricingPort;
import com.finance.common.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.cache.service.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CryptoPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Crypto> cacheService;
    private final CommissionProperties commissionProperties;
    private final CryptoRepository repository;

    public CryptoPricingStrategy(MarketCacheService<Crypto> cacheService,
                                 CommissionProperties commissionProperties,
                                 CryptoRepository repository) {
        this.cacheService = cacheService;
        this.commissionProperties = commissionProperties;
        this.repository = repository;
    }

    @Override
    public Map<String, BigDecimal> getAllPricesTry() {
        return repository.findAll().stream()
                .filter(c -> c.getCurrentPriceTry() != null)
                .collect(Collectors.toUnmodifiableMap(Crypto::getCode, Crypto::getCurrentPriceTry, (a, b) -> a));
    }

    @Override
    public Map<String, AssetSnapshot> getAllSnapshots() {
        return repository.findAll().stream()
                .filter(c -> c.getCurrentPriceTry() != null)
                .map(Crypto::toSnapshot)
                .collect(Collectors.toUnmodifiableMap(AssetSnapshot::code, s -> s, (a, b) -> a));
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
