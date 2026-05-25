package com.finance.market.crypto.service.assetpricing;

import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.repository.CryptoCandleRepository;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.service.ExchangeRateProvider;
import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class CryptoPricingStrategy extends BaseAssetPricingStrategy {

    private static final Set<String> TRY_QUOTED_IDS = Set.of("tether");
    private static final Set<String> TRY_QUOTED_TICKERS = Set.of("USDT");

    private final MarketCacheService<Crypto> cacheService;
    private final CryptoCandleRepository candleRepository;
    private final ExchangeRateProvider exchangeRateProvider;

    public CryptoPricingStrategy(MarketCacheService<Crypto> cacheService,
                                 CryptoCandleRepository candleRepository,
                                 ExchangeRateProvider exchangeRateProvider) {
        this.cacheService = cacheService;
        this.candleRepository = candleRepository;
        this.exchangeRateProvider = exchangeRateProvider;
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
        return candleRepository.findFirstByCryptoIdAndCloseGreaterThanOrderByCandleDateDesc(assetCode, BigDecimal.ZERO)
                .map(c -> normalize(convertCandleCloseToTry(assetCode, c.getClose())))
                .orElse(current);
    }

    private BigDecimal convertCandleCloseToTry(String assetCode, BigDecimal close) {
        if (close == null) return null;
        if (assetCode == null) return close;
        if (TRY_QUOTED_IDS.contains(assetCode.toLowerCase())
                || TRY_QUOTED_TICKERS.contains(assetCode.toUpperCase())) {
            return close;
        }
        BigDecimal usdTry = exchangeRateProvider.getCurrentUsdTry().currentRate();
        if (usdTry == null || usdTry.signum() <= 0) return close;
        return close.multiply(usdTry);
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
            price = candleRepository.findFirstByCryptoIdAndCloseGreaterThanOrderByCandleDateDesc(assetCode, BigDecimal.ZERO)
                    .map(c -> normalize(convertCandleCloseToTry(assetCode, c.getClose())))
                    .orElse(price);
        }
        return new AssetPricingPort.PriceBundle(
                price,
                new AssetPricingPort.AssetMeta(crypto.resolveDisplayName(), crypto.getImage()));
    }
}
