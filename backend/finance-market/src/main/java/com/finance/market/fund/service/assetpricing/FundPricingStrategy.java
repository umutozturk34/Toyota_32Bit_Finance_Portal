package com.finance.market.fund.service.assetpricing;

import com.finance.market.core.service.assetpricing.BaseAssetPricingStrategy;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;
import com.finance.market.core.cache.MarketCacheService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Prices TEFAS funds in TRY from the cached snapshot's unit price, falling back to the latest stored
 * candle with a positive price; funds are TRY-denominated so no FX conversion is applied.
 */
@Component
public class FundPricingStrategy extends BaseAssetPricingStrategy {

    private final MarketCacheService<Fund> cacheService;
    private final FundCandleRepository candleRepository;

    public FundPricingStrategy(MarketCacheService<Fund> cacheService,
                               FundCandleRepository candleRepository) {
        this.cacheService = cacheService;
        this.candleRepository = candleRepository;
    }

    @Override
    public MarketType marketType() {
        return MarketType.FUND;
    }

    @Override
    public BigDecimal getPriceTry(String assetCode) {
        Fund fund = cacheService.getSnapshot(assetCode);
        BigDecimal current = fund != null ? normalize(fund.getPrice()) : null;
        if (current != null && current.signum() > 0) return current;
        return candleRepository.findFirstByFundCodeAndPriceGreaterThanOrderByCandleDateDesc(assetCode, BigDecimal.ZERO)
                .map(c -> normalize(c.getPrice()))
                .orElse(current);
    }

    @Override
    public AssetPricingPort.AssetMeta getAssetMeta(String assetCode) {
        return baseMeta(cacheService.getSnapshot(assetCode));
    }

    @Override
    public AssetPricingPort.PriceBundle getBundle(String assetCode) {
        Fund fund = cacheService.getSnapshot(assetCode);
        if (fund == null) {
            return new AssetPricingPort.PriceBundle(null, EMPTY_META);
        }
        BigDecimal price = normalize(fund.getPrice());
        if (price == null || price.signum() <= 0) {
            price = candleRepository.findFirstByFundCodeAndPriceGreaterThanOrderByCandleDateDesc(assetCode, BigDecimal.ZERO)
                    .map(c -> normalize(c.getPrice()))
                    .orElse(price);
        }
        return new AssetPricingPort.PriceBundle(
                price,
                new AssetPricingPort.AssetMeta(fund.resolveDisplayName(), fund.getImage()));
    }
}
