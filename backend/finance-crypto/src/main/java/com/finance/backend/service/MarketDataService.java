package com.finance.backend.service;
import com.finance.backend.client.CoinGeckoClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoSnapshotDto;
import com.finance.backend.mapper.CryptoMapper;
import com.finance.backend.util.BatchFailureGuard;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.constants.MarketConstants;
import com.finance.backend.repository.CryptoCandleRepository;
import com.finance.backend.repository.CryptoRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
@Log4j2
@Service
public class MarketDataService {
    private final CoinGeckoClient coinGeckoClient;
    private final CryptoMapper cryptoMapper;
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final MarketConstants marketConstants;
    private final TransactionTemplate transactionTemplate;
    private final int historyDays;
    private final int minCandlesForHealthy;

    public MarketDataService(CoinGeckoClient coinGeckoClient,
                             CryptoMapper cryptoMapper,
                             CryptoRepository cryptoRepository,
                             CryptoCandleRepository cryptoCandleRepository,
                             MarketCacheService<Crypto, CryptoCandle> cryptoCacheService,
                             MarketConstants marketConstants,
                             PlatformTransactionManager transactionManager,
                             AppProperties appProperties) {
        this.coinGeckoClient = coinGeckoClient;
        this.cryptoMapper = cryptoMapper;
        this.cryptoRepository = cryptoRepository;
        this.cryptoCandleRepository = cryptoCandleRepository;
        this.cryptoCacheService = cryptoCacheService;
        this.marketConstants = marketConstants;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.historyDays = appProperties.getCrypto().getHistoryDays();
        this.minCandlesForHealthy = appProperties.getCrypto().getMinCandlesForHealthy();
    }
    public void updateOnlySnapshots() {
        List<String> trackedCoins = marketConstants.getTrackedCryptos();
        log.info("Starting crypto snapshot update for {} coins", trackedCoins.size());
        List<CoinGeckoSnapshotDto> usdMarkets = coinGeckoClient.fetchMarkets("usd", trackedCoins);
        List<CoinGeckoSnapshotDto> tryMarkets = coinGeckoClient.fetchMarkets("try", trackedCoins);
        Map<String, BigDecimal> tryPriceMap = tryMarkets.stream()
                .collect(Collectors.toMap(CoinGeckoSnapshotDto::id, CoinGeckoSnapshotDto::currentPrice));

        int successCount = 0;
        int failCount = 0;
        List<String> failedIds = new ArrayList<>();
        for (CoinGeckoSnapshotDto usdDto : usdMarkets) {
            try {
                BigDecimal tryPrice = tryPriceMap.get(usdDto.id());
                Crypto saved = transactionTemplate.execute(status -> saveSingleSnapshot(usdDto, tryPrice));
                cryptoCacheService.putSnapshot(saved.getId(), saved);
                successCount++;
            } catch (Exception e) {
                failCount++;
                failedIds.add(usdDto.id());
                log.error("Failed to save snapshot for {}: {}", usdDto.id(), e.getMessage(), e);
                BatchFailureGuard.check(successCount, failCount, failedIds, "snapshot");
            }
        }
        log.info("Crypto snapshot update: {} success, {} failed", successCount, failCount);
        if (!failedIds.isEmpty()) {
            log.warn("Failed coins: {}", failedIds);
        }
    }

    private Crypto saveSingleSnapshot(CoinGeckoSnapshotDto usdDto, BigDecimal tryPrice) {
        LocalDateTime now = LocalDateTime.now();
        Crypto existing = cryptoRepository.findById(usdDto.id()).orElse(null);
        if (existing != null) {
            cryptoMapper.updateEntityFromDto(existing, usdDto, tryPrice, now);
        } else {
            existing = cryptoMapper.toEntity(usdDto, tryPrice, now);
        }
        return cryptoRepository.save(existing);
    }
    public void updateOnlyCandles() {
        List<String> trackedCoins = marketConstants.getTrackedCryptos();
        log.info("Starting crypto candle update for {} coins", trackedCoins.size());
        int processed = 0;
        int failed = 0;
        List<String> failedCoins = new ArrayList<>();
        for (String coinId : trackedCoins) {
            try {
                long count = cryptoCandleRepository.countByCryptoId(coinId);
                if (count < minCandlesForHealthy) {
                    log.debug("{} - only {} candles (min {}), reloading full history", coinId, count, minCandlesForHealthy);
                    reloadFullHistory(coinId);
                } else {
                    int gapDays = cryptoCandleRepository.findFirstByCryptoIdOrderByCandleDateDesc(coinId)
                            .map(lastCandle -> (int) ChronoUnit.DAYS.between(lastCandle.getCandleDate().toLocalDate(), LocalDate.now()))
                            .orElse(historyDays);
                    if (gapDays >= historyDays) {
                        log.debug("{} - gap {} days >= history {}, reloading full", coinId, gapDays, historyDays);
                        reloadFullHistory(coinId);
                    } else {
                        log.debug("{} - filling {} day gap", coinId, gapDays);
                        fetchAndSaveSinceLastCandle(coinId, gapDays);
                    }
                }
                processed++;
                cryptoCacheService.refreshHistory(coinId);
            } catch (Exception e) {
                failed++;
                failedCoins.add(coinId);
                log.error("Failed to fetch candle for {}: {}", coinId, e.getMessage(), e);
                BatchFailureGuard.check(processed, failed, failedCoins, "candle");
            }
        }
        pruneOldCandles();
        log.info("Crypto candle update: {} success, {} failed", processed, failed);
        if (!failedCoins.isEmpty()) {
            log.warn("Failed coins: {}", failedCoins);
        }
    }
    public void fullMarketUpdate() {
        updateOnlySnapshots();
        updateOnlyCandles();
    }
    private void reloadFullHistory(String coinId) {
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchMarketChartRange(coinId, historyDays);
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
    private void fetchAndSaveSinceLastCandle(String coinId, int gapDays) {
        int fetchDays = Math.max(gapDays + 1, 2);
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchMarketChartRange(coinId, fetchDays);
        if (dtos.isEmpty()) return;

        Crypto crypto = cryptoRepository.getReferenceById(coinId);
        List<LocalDateTime> dates = dtos.stream()
                .map(dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS))
                .toList();

        transactionTemplate.executeWithoutResult(status -> {
            Map<LocalDateTime, CryptoCandle> existingMap = cryptoCandleRepository
                    .findByCryptoIdAndCandleDateIn(coinId, dates)
                    .stream()
                    .collect(Collectors.toMap(
                            c -> c.getCandleDate().truncatedTo(ChronoUnit.DAYS),
                            Function.identity(),
                            (a, b) -> a));

            List<CryptoCandle> toSave = new ArrayList<>(dtos.size());
            for (CoinGeckoCandleDto dto : dtos) {
                LocalDateTime normalizedDate = dto.candleDate().truncatedTo(ChronoUnit.DAYS);
                CryptoCandle existing = existingMap.get(normalizedDate);
                if (existing != null) {
                    cryptoMapper.updateCandleEntity(existing, dto);
                } else {
                    toSave.add(cryptoMapper.toCandleEntity(dto, crypto));
                }
            }
            if (!toSave.isEmpty()) {
                cryptoCandleRepository.saveAll(toSave);
            }
        });
    }
    private void pruneOldCandles() {
        LocalDateTime cutoffDate = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(historyDays - 1);
        transactionTemplate.executeWithoutResult(status -> {
            cryptoCandleRepository.deleteByCandleDateBefore(cutoffDate);
        });
    }
}
