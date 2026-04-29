package com.finance.backend.service;

import com.finance.backend.client.CoinGeckoClient;
import com.finance.backend.config.CryptoProperties;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.mapper.CryptoMapper;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CryptoCandleRepository;
import com.finance.backend.repository.CryptoRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CandlePruner;
import com.finance.backend.util.CodeNormalizer;
import com.finance.backend.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

@Log4j2
@Service
public class CryptoUpdateService implements MarketRefresher {

    private static final int BATCH_PARALLELISM = 5;

    private final CoinGeckoClient coinGeckoClient;
    private final CryptoMapper cryptoMapper;
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final CryptoSnapshotProcessor snapshotProcessor;
    private final CryptoEntityWriter entityWriter;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final CryptoSymbolResolver cryptoSymbolResolver;
    private final TransactionTemplate transactionTemplate;
    private final int historyDays;
    private final int minCandlesForHealthy;

    public CryptoUpdateService(CoinGeckoClient coinGeckoClient,
                               CryptoMapper cryptoMapper,
                               CryptoRepository cryptoRepository,
                               CryptoCandleRepository cryptoCandleRepository,
                               MarketCacheService<Crypto, CryptoCandle> cryptoCacheService,
                               CryptoSnapshotProcessor snapshotProcessor,
                               CryptoEntityWriter entityWriter,
                               TrackedAssetQueryService trackedAssetQueryService,
                               CryptoSymbolResolver cryptoSymbolResolver,
                               TransactionTemplate transactionTemplate,
                               CryptoProperties cryptoProperties) {
        this.coinGeckoClient = coinGeckoClient;
        this.cryptoMapper = cryptoMapper;
        this.cryptoRepository = cryptoRepository;
        this.cryptoCandleRepository = cryptoCandleRepository;
        this.cryptoCacheService = cryptoCacheService;
        this.snapshotProcessor = snapshotProcessor;
        this.entityWriter = entityWriter;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.cryptoSymbolResolver = cryptoSymbolResolver;
        this.transactionTemplate = transactionTemplate;
        this.historyDays = cryptoProperties.getHistoryDays();
        this.minCandlesForHealthy = cryptoProperties.getMinCandlesForHealthy();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.CRYPTO;
    }

    @Override
    public void refreshAll() {
        snapshotProcessor.refreshAll();
        refreshAllCandles();
    }

    @Override
    public void refresh(String coinId) {
        snapshotProcessor.refreshOne(coinId);
        String normalizedId = CodeNormalizer.lower(coinId);
        if (normalizedId.isBlank()) return;
        updateCandlesForCoin(normalizedId);
        cryptoCacheService.refreshHistory(normalizedId);
        log.info("Refreshed tracked crypto candles for {}", normalizedId);
    }

    public boolean exists(String code) {
        return snapshotProcessor.exists(code);
    }

    private void refreshAllCandles() {
        List<String> trackedCoins = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.CRYPTO);
        log.info("Starting crypto candle update for {} coins", trackedCoins.size());
        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                trackedCoins,
                coinId -> {
                    updateCandlesForCoin(coinId);
                    cryptoCacheService.refreshHistory(coinId);
                },
                Function.identity(),
                log, "Crypto", "candle", BATCH_PARALLELISM);

        CandlePruner.pruneByDays(transactionTemplate, historyDays,
                cutoffDate -> cryptoCandleRepository.deleteByCandleDateBefore(cutoffDate));
        BatchLogHelper.logSummary(log, "Crypto candle update", result);
    }

    private void updateCandlesForCoin(String coinId) {
        String binanceSymbol = cryptoSymbolResolver.resolveBinanceSymbol(coinId);
        if (binanceSymbol == null) {
            log.warn("No Binance mapping for coinId: {}, skipping candle update", coinId);
            return;
        }
        long count = cryptoCandleRepository.countByCryptoId(coinId);
        if (count < minCandlesForHealthy) {
            reloadFullHistory(coinId, binanceSymbol);
            return;
        }
        int gapDays = cryptoCandleRepository.findFirstByCryptoIdOrderByCandleDateDesc(coinId)
                .map(lastCandle -> (int) ChronoUnit.DAYS.between(lastCandle.getCandleDate().toLocalDate(), LocalDate.now()))
                .orElse(historyDays);
        if (gapDays >= historyDays) {
            reloadFullHistory(coinId, binanceSymbol);
        } else {
            fetchAndSaveSinceLastCandle(coinId, binanceSymbol, gapDays);
        }
    }

    private void reloadFullHistory(String coinId, String binanceSymbol) {
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchBinanceKlines(coinId, binanceSymbol, historyDays);
        if (dtos.isEmpty()) return;
        log.info("{} - reloading full history: {} candles", coinId, dtos.size());
        Crypto crypto = cryptoRepository.getReferenceById(coinId);
        List<CryptoCandle> candles = dtos.stream()
                .map(dto -> cryptoMapper.toCandleEntity(dto, crypto))
                .toList();
        transactionTemplate.executeWithoutResult(status ->
                entityWriter.replaceCandleHistory(coinId, candles));
    }

    private void fetchAndSaveSinceLastCandle(String coinId, String binanceSymbol, int gapDays) {
        int fetchDays = Math.max(gapDays + 1, 2);
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchBinanceKlines(coinId, binanceSymbol, fetchDays);
        if (dtos.isEmpty()) return;
        Crypto crypto = cryptoRepository.getReferenceById(coinId);
        transactionTemplate.executeWithoutResult(status ->
                entityWriter.upsertCandles(coinId, crypto, dtos));
    }
}
