package com.finance.market.crypto.service;
import com.finance.market.core.service.MarketSnapshotProcessor;

import com.finance.market.core.service.TrackedAssetQueryService;

import com.finance.market.core.cache.MarketCacheService;


import com.finance.market.crypto.client.CoinGeckoClient;
import com.finance.market.crypto.config.CryptoProperties;
import com.finance.market.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.market.crypto.dto.external.CoinGeckoSnapshotDto;
import com.finance.market.crypto.model.Crypto;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.util.ApiAssetValidator;
import com.finance.shared.util.BatchLogHelper;
import com.finance.shared.util.BatchUpdateRunner;
import com.finance.shared.util.CodeNormalizer;
import com.finance.market.core.util.MarketBatchRunner;
import com.finance.market.core.util.TrackedRefreshRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Refreshes crypto snapshots from CoinGecko, fetching the same coins in both USD and TRY so each
 * entity stores its native USD price and a TRY price. Existence checks validate both the CoinGecko
 * id and (when given) a Binance symbol.
 */
@Log4j2
@Component
public class CryptoSnapshotProcessor implements MarketSnapshotProcessor {

    private final CoinGeckoClient coinGeckoClient;
    private final CryptoEntityWriter entityWriter;
    private final MarketCacheService<Crypto> cryptoCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;
    private final int batchMinSample;
    private final String vsUsd;
    private final String vsTry;

    public CryptoSnapshotProcessor(CoinGeckoClient coinGeckoClient,
                                    CryptoEntityWriter entityWriter,
                                    MarketCacheService<Crypto> cryptoCacheService,
                                    TrackedAssetQueryService trackedAssetQueryService,
                                    TransactionTemplate transactionTemplate,
                                    CryptoProperties cryptoProperties) {
        this.coinGeckoClient = coinGeckoClient;
        this.entityWriter = entityWriter;
        this.cryptoCacheService = cryptoCacheService;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.transactionTemplate = transactionTemplate;
        this.batchMinSample = cryptoProperties.getBatchMinSample();
        this.vsUsd = cryptoProperties.getVsUsd();
        this.vsTry = cryptoProperties.getVsTry();
    }

    /** Refreshes every tracked coin, fetching the whole set once in USD and once in TRY before per-coin upsert. */
    public void refreshAll() {
        List<String> trackedCoins = trackedAssetQueryService.getCodes(TrackedAssetType.CRYPTO);
        log.info("Starting crypto snapshot update for {} coins", trackedCoins.size());
        List<CoinGeckoSnapshotDto> usdMarkets = coinGeckoClient.fetchMarkets(vsUsd, trackedCoins);
        List<CoinGeckoSnapshotDto> tryMarkets = coinGeckoClient.fetchMarkets(vsTry, trackedCoins);
        Map<String, BigDecimal> tryPriceMap = tryMarkets.stream()
                .collect(Collectors.toMap(CoinGeckoSnapshotDto::id, CoinGeckoSnapshotDto::currentPrice));

        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                usdMarkets,
                usdDto -> {
                    BigDecimal tryPrice = tryPriceMap.get(usdDto.id());
                    Crypto saved = transactionTemplate.execute(status ->
                            entityWriter.saveSnapshot(usdDto, tryPrice));
                    cryptoCacheService.putSnapshot(saved.getId(), saved);
                },
                CoinGeckoSnapshotDto::id,
                log, "Crypto", "snapshot", batchMinSample);

        BatchLogHelper.logSummary(log, "Crypto snapshot update", result);
    }

    public void refreshOne(String coinId) {
        TrackedRefreshRunner.refreshSnapshot(coinId, CodeNormalizer::lower, normalizedId -> {
            List<CoinGeckoSnapshotDto> usdMarkets = coinGeckoClient.fetchMarkets(vsUsd, List.of(normalizedId));
            if (usdMarkets.isEmpty()) {
                log.warn("No USD snapshot found for tracked crypto {}", normalizedId);
                return false;
            }
            List<CoinGeckoSnapshotDto> tryMarkets = coinGeckoClient.fetchMarkets(vsTry, List.of(normalizedId));
            BigDecimal tryPrice = tryMarkets.isEmpty() ? null : tryMarkets.getFirst().currentPrice();
            Crypto saved = transactionTemplate.execute(status ->
                    entityWriter.saveSnapshot(usdMarkets.getFirst(), tryPrice));
            cryptoCacheService.putSnapshot(saved.getId(), saved);
            return true;
        }, log, "crypto");
    }

    public boolean exists(String coinId) {
        return exists(coinId, null);
    }

    /** Coin exists if CoinGecko knows the id and, when a Binance symbol is given, klines are available. */
    public boolean exists(String coinId, String binanceSymbol) {
        boolean coinGeckoExists = ApiAssetValidator.validate(coinId, false, cid -> {
            List<CoinGeckoSnapshotDto> result = coinGeckoClient.fetchMarkets(vsUsd, List.of(cid));
            return !result.isEmpty();
        }, log, "Crypto");
        if (!coinGeckoExists) return false;
        if (binanceSymbol == null || binanceSymbol.isBlank()) return true;
        return ApiAssetValidator.validate(binanceSymbol, true, sym -> {
            List<CoinGeckoCandleDto> klines = coinGeckoClient.fetchBinanceKlines(coinId, sym, 0L, 1);
            return !klines.isEmpty();
        }, log, "Binance");
    }
}
