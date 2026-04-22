package com.finance.backend.service;

import com.finance.backend.client.CoinGeckoClient;
import com.finance.backend.config.AppProperties;
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
import com.finance.backend.util.CandleBatchUpsertTemplate;
import com.finance.backend.util.CandlePruner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

@Log4j2
@Service
public class CryptoCandleService implements CandleBatchRefresher {

    private final CoinGeckoClient coinGeckoClient;
    private final CryptoMapper cryptoMapper;
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final CryptoSymbolResolver cryptoSymbolResolver;
    private final TransactionTemplate transactionTemplate;
    private final int historyDays;
    private final int minCandlesForHealthy;

    public CryptoCandleService(CoinGeckoClient coinGeckoClient,
                               CryptoMapper cryptoMapper,
                               CryptoRepository cryptoRepository,
                               CryptoCandleRepository cryptoCandleRepository,
                               MarketCacheService<Crypto, CryptoCandle> cryptoCacheService,
                               TrackedAssetQueryService trackedAssetQueryService,
                               CryptoSymbolResolver cryptoSymbolResolver,
                               TransactionTemplate transactionTemplate,
                               AppProperties appProperties) {
        this.coinGeckoClient = coinGeckoClient;
        this.cryptoMapper = cryptoMapper;
        this.cryptoRepository = cryptoRepository;
        this.cryptoCandleRepository = cryptoCandleRepository;
        this.cryptoCacheService = cryptoCacheService;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.cryptoSymbolResolver = cryptoSymbolResolver;
        this.transactionTemplate = transactionTemplate;
        this.historyDays = appProperties.getCrypto().getHistoryDays();
        this.minCandlesForHealthy = appProperties.getCrypto().getMinCandlesForHealthy();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.CRYPTO;
    }

    @Override
    public void refreshAll() {
        List<String> trackedCoins = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.CRYPTO);
        log.info("Starting crypto candle update for {} coins", trackedCoins.size());
        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                trackedCoins,
                coinId -> {
                    String binanceSymbol = cryptoSymbolResolver.resolveBinanceSymbol(coinId);
                    if (binanceSymbol == null) {
                        log.warn("No Binance mapping for coinId: {}, skipping candle update", coinId);
                        return;
                    }

                    long count = cryptoCandleRepository.countByCryptoId(coinId);
                    if (count < minCandlesForHealthy) {
                        log.debug("{} - only {} candles (min {}), reloading full history", coinId, count, minCandlesForHealthy);
                        reloadFullHistory(coinId, binanceSymbol);
                    } else {
                        int gapDays = cryptoCandleRepository.findFirstByCryptoIdOrderByCandleDateDesc(coinId)
                                .map(lastCandle -> (int) ChronoUnit.DAYS.between(lastCandle.getCandleDate().toLocalDate(), LocalDate.now()))
                                .orElse(historyDays);
                        if (gapDays >= historyDays) {
                            log.debug("{} - gap {} days >= history {}, reloading full", coinId, gapDays, historyDays);
                            reloadFullHistory(coinId, binanceSymbol);
                        } else {
                            log.debug("{} - filling {} day gap", coinId, gapDays);
                            fetchAndSaveSinceLastCandle(coinId, binanceSymbol, gapDays);
                        }
                    }

                    cryptoCacheService.refreshHistory(coinId);
                },
                Function.identity(),
                "candle",
                5,
                (coinId, e) -> log.error("Failed to fetch candle for {}: {}", coinId, e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("Crypto candle batch stopped early (circuit breaker open): {} success, {} failed",
                        stopped.successCount(), stopped.failCount()));

        pruneOldCandles();
        BatchLogHelper.logSummary(log, "Crypto candle update", result);
    }

    public void refreshTrackedCryptoCandles(String coinId) {
        String normalizedId = coinId == null ? "" : coinId.trim().toLowerCase();
        if (normalizedId.isBlank()) {
            return;
        }

        String binanceSymbol = cryptoSymbolResolver.resolveBinanceSymbol(normalizedId);
        if (binanceSymbol == null) {
            log.warn("No Binance mapping for coinId: {}, skipping tracked candle refresh", normalizedId);
            return;
        }

        long count = cryptoCandleRepository.countByCryptoId(normalizedId);
        if (count < minCandlesForHealthy) {
            reloadFullHistory(normalizedId, binanceSymbol);
        } else {
            int gapDays = cryptoCandleRepository.findFirstByCryptoIdOrderByCandleDateDesc(normalizedId)
                    .map(lastCandle -> (int) ChronoUnit.DAYS.between(lastCandle.getCandleDate().toLocalDate(), LocalDate.now()))
                    .orElse(historyDays);
            if (gapDays >= historyDays) {
                reloadFullHistory(normalizedId, binanceSymbol);
            } else {
                fetchAndSaveSinceLastCandle(normalizedId, binanceSymbol, gapDays);
            }
        }

        cryptoCacheService.refreshHistory(normalizedId);
        log.info("Refreshed tracked crypto candles for {}", normalizedId);
    }

    private void reloadFullHistory(String coinId, String binanceSymbol) {
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchBinanceKlines(coinId, binanceSymbol, historyDays);
        if (!dtos.isEmpty()) {
            log.info("{} - reloading full history: {} candles", coinId, dtos.size());
            Crypto crypto = cryptoRepository.getReferenceById(coinId);
            List<CryptoCandle> candles = dtos.stream()
                    .map(dto -> cryptoMapper.toCandleEntity(dto, crypto))
                    .toList();
            transactionTemplate.executeWithoutResult(status -> {
                cryptoCandleRepository.deleteByCryptoId(coinId);
                cryptoCandleRepository.saveAll(candles);
            });
        }
    }

    private void fetchAndSaveSinceLastCandle(String coinId, String binanceSymbol, int gapDays) {
        int fetchDays = Math.max(gapDays + 1, 2);
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchBinanceKlines(coinId, binanceSymbol, fetchDays);
        if (dtos.isEmpty()) {
            return;
        }

        Crypto crypto = cryptoRepository.getReferenceById(coinId);

        transactionTemplate.executeWithoutResult(status -> {
            CandleBatchUpsertTemplate.UpsertResult<CryptoCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                    dtos,
                    CoinGeckoCandleDto::candleDate,
                    keys -> cryptoCandleRepository.findByCryptoIdAndCandleDateIn(coinId, keys),
                    CryptoCandle::getCandleDate,
                    cryptoMapper::updateCandleEntity,
                    dto -> cryptoMapper.toCandleEntity(dto, crypto));

            if (!upsertResult.newEntities().isEmpty()) {
                cryptoCandleRepository.saveAll(upsertResult.newEntities());
            }
        });
    }

    private void pruneOldCandles() {
        CandlePruner.pruneByDays(
                transactionTemplate,
                historyDays,
                cutoffDate -> cryptoCandleRepository.deleteByCandleDateBefore(cutoffDate));
    }
}
