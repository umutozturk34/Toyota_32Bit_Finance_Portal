package com.finance.backend.config;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Log4j2
@Component
@RequiredArgsConstructor
public class AssetPricingAdapter implements AssetPricingPort {

    private static final int PRICE_SCALE = 4;

    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final AppProperties appProperties;

    @Override
    public BigDecimal getPriceTry(String assetType, String assetCode) {
        try {
            return switch (assetType) {
                case "CRYPTO" -> getCryptoPrice(assetCode);
                case "STOCK" -> getStockPrice(assetCode);
                case "FOREX" -> getForexPrice(assetCode);
                case "FUND" -> getFundPrice(assetCode);
                default -> {
                    log.warn("Unknown asset type: {}", assetType);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.warn("Failed to get price for {}:{} - {}", assetType, assetCode, e.getMessage());
            return null;
        }
    }

    @Override
    public BigDecimal getSellPriceTry(String assetType, String assetCode) {
        try {
            return switch (assetType) {
                case "CRYPTO" -> applyCommission(getCryptoPrice(assetCode), appProperties.getCommission().getCryptoRate());
                case "STOCK" -> applyCommission(getStockPrice(assetCode), appProperties.getCommission().getStockRate());
                case "FOREX" -> getForexSellPrice(assetCode);
                case "FUND" -> applyCommission(getFundPrice(assetCode), appProperties.getCommission().getFundRate());
                default -> {
                    log.warn("Unknown asset type: {}", assetType);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.warn("Failed to get sell price for {}:{} - {}", assetType, assetCode, e.getMessage());
            return null;
        }
    }

    @Override
    public AssetMeta getAssetMeta(String assetType, String assetCode) {
        try {
            return switch (assetType) {
                case "CRYPTO" -> {
                    Crypto c = cryptoCacheService.getSnapshot(assetCode);
                    yield c != null
                            ? new AssetMeta(resolveAssetName(c), c.getImage())
                            : new AssetMeta(null, null);
                }
                case "STOCK" -> {
                    Stock s = stockCacheService.getSnapshot(assetCode);
                    yield s != null ? new AssetMeta(resolveAssetName(s), null) : new AssetMeta(null, null);
                }
                case "FOREX" -> {
                    Forex f = forexCacheService.getSnapshot(assetCode);
                    yield f != null ? new AssetMeta(resolveAssetName(f), null) : new AssetMeta(null, null);
                }
                case "FUND" -> {
                    Fund fd = fundCacheService.getSnapshot(assetCode);
                    yield fd != null ? new AssetMeta(resolveAssetName(fd), null) : new AssetMeta(null, null);
                }
                default -> new AssetMeta(null, null);
            };
        } catch (Exception e) {
            log.warn("Failed to get metadata for {}:{} - {}", assetType, assetCode, e.getMessage());
            return new AssetMeta(null, null);
        }
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
        if (asset == null) {
            return null;
        }
        return switch (asset) {
            case Crypto c -> firstNonBlank(c.getName(), c.getSymbol(), c.getId());
            case Stock s -> firstNonBlank(s.getName(), s.getSymbol());
            case Forex f -> firstNonBlank(f.getName(), f.getCurrencyNameTr(), f.getCurrencyName(), f.getCurrencyCode());
            case Fund fu -> firstNonBlank(fu.getName(), fu.getFundCode());
            default -> null;
        };
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
