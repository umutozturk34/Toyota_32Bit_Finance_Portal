package com.finance.backend.service;

import com.finance.backend.client.CoinGeckoClient;
import com.finance.backend.dto.external.CoinGeckoSnapshotDto;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.util.ApiAssetValidator;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CodeNormalizer;
import com.finance.backend.util.MarketBatchRunner;
import com.finance.backend.util.TrackedRefreshRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class CryptoSnapshotProcessor implements MarketSnapshotProcessor {

    private static final int BATCH_PARALLELISM = 5;
    private static final String VS_USD = "usd";
    private static final String VS_TRY = "try";

    private final CoinGeckoClient coinGeckoClient;
    private final CryptoEntityWriter entityWriter;
    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;

    public void refreshAll() {
        List<String> trackedCoins = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.CRYPTO);
        log.info("Starting crypto snapshot update for {} coins", trackedCoins.size());
        List<CoinGeckoSnapshotDto> usdMarkets = coinGeckoClient.fetchMarkets(VS_USD, trackedCoins);
        List<CoinGeckoSnapshotDto> tryMarkets = coinGeckoClient.fetchMarkets(VS_TRY, trackedCoins);
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
                log, "Crypto", "snapshot", BATCH_PARALLELISM);

        BatchLogHelper.logSummary(log, "Crypto snapshot update", result);
    }

    public void refreshOne(String coinId) {
        TrackedRefreshRunner.refreshSnapshot(coinId, CodeNormalizer::lower, normalizedId -> {
            List<CoinGeckoSnapshotDto> usdMarkets = coinGeckoClient.fetchMarkets(VS_USD, List.of(normalizedId));
            if (usdMarkets.isEmpty()) {
                log.warn("No USD snapshot found for tracked crypto {}", normalizedId);
                return false;
            }
            List<CoinGeckoSnapshotDto> tryMarkets = coinGeckoClient.fetchMarkets(VS_TRY, List.of(normalizedId));
            BigDecimal tryPrice = tryMarkets.isEmpty() ? null : tryMarkets.getFirst().currentPrice();
            Crypto saved = transactionTemplate.execute(status ->
                    entityWriter.saveSnapshot(usdMarkets.getFirst(), tryPrice));
            cryptoCacheService.putSnapshot(saved.getId(), saved);
            return true;
        }, log, "crypto");
    }

    public boolean exists(String coinId) {
        return ApiAssetValidator.validate(coinId, false, cid -> {
            List<CoinGeckoSnapshotDto> result = coinGeckoClient.fetchMarkets(VS_USD, List.of(cid));
            return !result.isEmpty();
        }, log, "Crypto");
    }
}
