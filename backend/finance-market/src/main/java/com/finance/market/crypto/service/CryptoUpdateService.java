package com.finance.market.crypto.service;
import com.finance.market.core.service.MarketRefresher;

import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.market.crypto.client.CoinGeckoClient;
import com.finance.common.config.AppProperties;
import com.finance.market.crypto.config.CryptoProperties;
import com.finance.market.crypto.dto.external.CoinGeckoCandleDto;
import com.finance.market.crypto.mapper.CryptoMapper;
import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.model.CryptoCandle;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.crypto.repository.CryptoCandleRepository;
import com.finance.market.crypto.repository.CryptoRepository;
import com.finance.common.util.BatchLogHelper;
import com.finance.common.util.BatchUpdateRunner;
import com.finance.common.util.CodeNormalizer;
import com.finance.market.core.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Log4j2
@Service
public class CryptoUpdateService implements MarketRefresher {

    private final CoinGeckoClient coinGeckoClient;
    private final CryptoMapper cryptoMapper;
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final CryptoSnapshotProcessor snapshotProcessor;
    private final CryptoEntityWriter entityWriter;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final CryptoSymbolResolver cryptoSymbolResolver;
    private final TransactionTemplate transactionTemplate;
    private final int historyDays;
    private final int minCandlesForHealthy;
    private final int batchMinSample;
    private final int binancePageSize;
    private final ZoneId appZone;

    public CryptoUpdateService(CoinGeckoClient coinGeckoClient,
                               CryptoMapper cryptoMapper,
                               CryptoRepository cryptoRepository,
                               CryptoCandleRepository cryptoCandleRepository,
                               CryptoSnapshotProcessor snapshotProcessor,
                               CryptoEntityWriter entityWriter,
                               TrackedAssetQueryService trackedAssetQueryService,
                               CryptoSymbolResolver cryptoSymbolResolver,
                               TransactionTemplate transactionTemplate,
                               AppProperties appProperties,
                               CryptoProperties cryptoProperties) {
        this.coinGeckoClient = coinGeckoClient;
        this.cryptoMapper = cryptoMapper;
        this.cryptoRepository = cryptoRepository;
        this.cryptoCandleRepository = cryptoCandleRepository;
        this.snapshotProcessor = snapshotProcessor;
        this.entityWriter = entityWriter;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.cryptoSymbolResolver = cryptoSymbolResolver;
        this.transactionTemplate = transactionTemplate;
        this.historyDays = cryptoProperties.getHistoryDays();
        this.minCandlesForHealthy = cryptoProperties.getMinCandlesForHealthy();
        this.batchMinSample = cryptoProperties.getBatchMinSample();
        this.binancePageSize = cryptoProperties.getBinancePageSize();
        this.appZone = ZoneId.of(appProperties.getTimezone());
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
        log.info("Refreshed tracked crypto candles for {}", normalizedId);
    }

    public boolean exists(String code) {
        return snapshotProcessor.exists(code);
    }

    private void refreshAllCandles() {
        List<String> trackedCoins = trackedAssetQueryService.getCodes(TrackedAssetType.CRYPTO);
        log.info("Starting crypto candle update for {} coins", trackedCoins.size());
        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                trackedCoins,
                coinId -> {
                    updateCandlesForCoin(coinId);
                },
                Function.identity(),
                log, "Crypto", "candle", batchMinSample);

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
        List<CoinGeckoCandleDto> all = new ArrayList<>();
        long startTime = 0L;
        while (true) {
            List<CoinGeckoCandleDto> page = coinGeckoClient.fetchBinanceKlines(
                    coinId, binanceSymbol, startTime, binancePageSize);
            if (page.isEmpty()) break;
            all.addAll(page);
            if (page.size() < binancePageSize) break;
            startTime = nextStartTime(page.get(page.size() - 1).candleDate());
        }
        if (all.isEmpty()) return;
        log.info("{} - reloading full history: {} candles", coinId, all.size());
        Crypto crypto = cryptoRepository.getReferenceById(coinId);
        List<CryptoCandle> candles = all.stream()
                .map(dto -> cryptoMapper.toCandleEntity(dto, crypto))
                .toList();
        transactionTemplate.executeWithoutResult(status ->
                entityWriter.replaceCandleHistory(coinId, candles));
    }

    private void fetchAndSaveSinceLastCandle(String coinId, String binanceSymbol, int gapDays) {
        long startTime = cryptoCandleRepository.findFirstByCryptoIdOrderByCandleDateDesc(coinId)
                .map(c -> nextStartTime(c.getCandleDate()))
                .orElse(0L);
        int limit = Math.max(gapDays + 1, 2);
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchBinanceKlines(
                coinId, binanceSymbol, startTime, limit);
        if (dtos.isEmpty()) return;
        Crypto crypto = cryptoRepository.getReferenceById(coinId);
        transactionTemplate.executeWithoutResult(status ->
                entityWriter.upsertCandles(coinId, crypto, dtos));
    }

    private long nextStartTime(LocalDateTime lastCandleDate) {
        return lastCandleDate.plusDays(1).atZone(appZone).toInstant().toEpochMilli();
    }
}
