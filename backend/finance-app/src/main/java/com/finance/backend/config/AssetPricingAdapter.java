package com.finance.backend.config;

import com.finance.backend.model.BaseAsset;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.service.AssetPricingPort;
import com.finance.backend.service.MarketCacheService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.Function;

@Log4j2
@Component
public class AssetPricingAdapter implements AssetPricingPort {

    private static final int PRICE_SCALE = 4;
    private static final AssetMeta EMPTY_META = new AssetMeta(null, null);

    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final AppProperties appProperties;

    private final Map<String, Function<String, BigDecimal>> priceLookups;
    private final Map<String, Function<String, BigDecimal>> sellPriceLookups;
    private final Map<String, Function<String, AssetMeta>> metaLookups;

    public AssetPricingAdapter(MarketCacheService<Crypto, CryptoCandle> cryptoCacheService,
                               MarketCacheService<Stock, StockCandle> stockCacheService,
                               MarketCacheService<Forex, ForexCandle> forexCacheService,
                               MarketCacheService<Fund, FundCandle> fundCacheService,
                               AppProperties appProperties) {
        this.cryptoCacheService = cryptoCacheService;
        this.stockCacheService = stockCacheService;
        this.forexCacheService = forexCacheService;
        this.fundCacheService = fundCacheService;
        this.appProperties = appProperties;
        this.priceLookups = Map.of(
                "CRYPTO", this::getCryptoPrice,
                "STOCK", this::getStockPrice,
                "FOREX", this::getForexPrice,
                "FUND", this::getFundPrice);
        this.sellPriceLookups = Map.of(
                "CRYPTO", code -> applyCommission(getCryptoPrice(code), appProperties.getCommission().getCryptoRate()),
                "STOCK", code -> applyCommission(getStockPrice(code), appProperties.getCommission().getStockRate()),
                "FOREX", this::getForexSellPrice,
                "FUND", code -> applyCommission(getFundPrice(code), appProperties.getCommission().getFundRate()));
        this.metaLookups = Map.of(
                "CRYPTO", code -> cryptoMeta(cryptoCacheService.getSnapshot(code)),
                "STOCK", code -> baseMeta(stockCacheService.getSnapshot(code)),
                "FOREX", code -> baseMeta(forexCacheService.getSnapshot(code)),
                "FUND", code -> baseMeta(fundCacheService.getSnapshot(code)));
    }

    @Override
    public BigDecimal getPriceTry(String assetType, String assetCode) {
        return dispatch(priceLookups, assetType, assetCode, "price", null);
    }

    @Override
    public BigDecimal getSellPriceTry(String assetType, String assetCode) {
        return dispatch(sellPriceLookups, assetType, assetCode, "sell price", null);
    }

    @Override
    public AssetMeta getAssetMeta(String assetType, String assetCode) {
        return dispatch(metaLookups, assetType, assetCode, "metadata", EMPTY_META);
    }

    private <T> T dispatch(Map<String, Function<String, T>> lookups, String assetType, String assetCode, String label, T fallback) {
        Function<String, T> fn = lookups.get(assetType);
        if (fn == null) {
            log.warn("Unknown asset type: {}", assetType);
            return fallback;
        }
        try {
            return fn.apply(assetCode);
        } catch (Exception e) {
            log.warn("Failed to get {} for {}:{} - {}", label, assetType, assetCode, e.getMessage());
            return fallback;
        }
    }

    private AssetMeta cryptoMeta(Crypto crypto) {
        return crypto != null ? new AssetMeta(resolveAssetName(crypto), crypto.getImage()) : EMPTY_META;
    }

    private AssetMeta baseMeta(BaseAsset asset) {
        return asset != null ? new AssetMeta(resolveAssetName(asset), null) : EMPTY_META;
    }

    private BigDecimal getCryptoPrice(String assetCode) {
        Crypto crypto = cryptoCacheService.getSnapshot(assetCode);
        return crypto != null ? normalize(crypto.getCurrentPriceTry()) : null;
    }

    private BigDecimal getStockPrice(String assetCode) {
        Stock stock = stockCacheService.getSnapshot(assetCode);
        return stock != null ? normalize(stock.getCurrentPrice()) : null;
    }

    private BigDecimal getForexPrice(String assetCode) {
        Forex forex = forexCacheService.getSnapshot(assetCode);
        if (forex == null) return null;
        return normalize(forex.getSellingPrice() != null ? forex.getSellingPrice() : forex.getCurrentPrice());
    }

    private BigDecimal getFundPrice(String assetCode) {
        Fund fund = fundCacheService.getSnapshot(assetCode);
        return fund != null ? normalize(fund.getPrice()) : null;
    }

    private BigDecimal getForexSellPrice(String assetCode) {
        Forex forex = forexCacheService.getSnapshot(assetCode);
        if (forex == null) return null;
        return normalize(forex.getCurrentPrice());
    }

    private BigDecimal applyCommission(BigDecimal price, BigDecimal rate) {
        if (price == null) return null;
        return normalize(price.multiply(BigDecimal.ONE.subtract(rate)));
    }

    private BigDecimal normalize(BigDecimal price) {
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private String resolveAssetName(Object asset) {
        return asset instanceof BaseAsset base ? base.resolveDisplayName() : null;
    }
}
