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

/**
 * Prices crypto in TRY candle-first: the latest Binance candle close (crypto_candles) converted at the
 * current USD/TRY rate, with the CoinGecko snapshot (cryptos.current_price_try) only as a fallback when no
 * candle exists. This is the SAME basis the performance chart/backfill and entry cost use, so the whole
 * portfolio (card, per-position rows, allocation pie, chart) values crypto identically. Tether/USDT is
 * already TRY-quoted, so its close is not cross-converted.
 */
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
        return candleFirstPriceTry(assetCode);
    }

    @Override
    public BigDecimal getExitPriceTry(String assetCode) {
        return candleFirstPriceTry(assetCode);
    }

    /**
     * The portfolio's single crypto price basis: the latest Binance candle close (crypto_candles) converted
     * at the current USD/TRY SELLING spot — the same price source and FX field the entry cost and the
     * chart/snapshot backfill use ({@code HistoricalPricingAdapter}: candle close × forex selling). The
     * CoinGecko snapshot ({@code cryptos.current_price_try}) is a fallback ONLY when no candle exists yet
     * (e.g. a brand-new listing). Crypto ingests two independent feeds — Binance klines and the CoinGecko
     * markets snapshot — that diverge by a fraction of a percent; pricing the live card, per-position rows
     * and allocation off the candle keeps the whole portfolio on ONE basis (card == chart) instead of the
     * card reading CoinGecko while the chart reads the candle. USDT/tether stays TRY-native via
     * {@link #convertCandleCloseToTry}. The market screen prices crypto from the CoinGecko snapshot directly
     * and is unaffected — it does not go through this strategy.
     */
    private BigDecimal candleFirstPriceTry(String assetCode) {
        BigDecimal candleClose = candleRepository
                .findFirstByCryptoIdAndCloseGreaterThanOrderByCandleDateDesc(assetCode, BigDecimal.ZERO)
                .map(c -> normalize(convertCandleCloseToTry(assetCode, c.getClose())))
                .orElse(null);
        if (candleClose != null && candleClose.signum() > 0) return candleClose;
        Crypto crypto = cacheService.getSnapshot(assetCode);
        return crypto != null ? normalize(crypto.getCurrentPriceTry()) : null;
    }

    /** Converts a USD candle close to TRY at the current spot, leaving tether/USDT closes as-is. */
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
        AssetPricingPort.AssetMeta meta = crypto != null
                ? new AssetPricingPort.AssetMeta(crypto.resolveDisplayName(), crypto.getImage())
                : EMPTY_META;
        // Price candle-first (same basis as getPriceTry) so a held position's per-row value matches the card
        // and the performance chart; meta still comes from the CoinGecko snapshot. Candle-less new listings
        // fall back to the snapshot price inside candleFirstPriceTry.
        return new AssetPricingPort.PriceBundle(candleFirstPriceTry(assetCode), meta);
    }
}
