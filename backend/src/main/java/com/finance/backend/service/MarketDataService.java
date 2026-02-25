package com.finance.backend.service;
import com.finance.backend.client.CoinGeckoClient;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoMarketDto;
import com.finance.backend.mapper.CryptoMapper;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.constants.MarketConstants;
import com.finance.backend.repository.CryptoCandleRepository;
import com.finance.backend.repository.CryptoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
@Slf4j
@Service
public class MarketDataService {
    private static final int HISTORY_DAYS = 365;
    private static final int MIN_CANDLES_FOR_HEALTHY = 350;
    private final CoinGeckoClient coinGeckoClient;
    private final CryptoMapper cryptoMapper;
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final CryptoCacheService cryptoCacheService;
    private final MarketConstants marketConstants;
    private final TransactionTemplate transactionTemplate;
    public MarketDataService(CoinGeckoClient coinGeckoClient,
                             CryptoMapper cryptoMapper,
                             CryptoRepository cryptoRepository,
                             CryptoCandleRepository cryptoCandleRepository,
                             CryptoCacheService cryptoCacheService,
                             MarketConstants marketConstants,
                             PlatformTransactionManager transactionManager) {
        this.coinGeckoClient = coinGeckoClient;
        this.cryptoMapper = cryptoMapper;
        this.cryptoRepository = cryptoRepository;
        this.cryptoCandleRepository = cryptoCandleRepository;
        this.cryptoCacheService = cryptoCacheService;
        this.marketConstants = marketConstants;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }
    public void updateOnlySnapshots() {
        List<String> trackedCoins = marketConstants.getTrackedCryptos();
        log.info("Starting snapshot update for {} coins...", trackedCoins.size());
        List<CoinGeckoMarketDto> usdMarkets = coinGeckoClient.fetchMarkets("usd", trackedCoins);
        List<CoinGeckoMarketDto> tryMarkets = coinGeckoClient.fetchMarkets("try", trackedCoins);
        Map<String, BigDecimal> tryPriceMap = tryMarkets.stream()
                .collect(Collectors.toMap(CoinGeckoMarketDto::id, CoinGeckoMarketDto::currentPrice));
        LocalDateTime now = LocalDateTime.now();
        Map<String, Crypto> existingMap = cryptoRepository.findAllById(
                usdMarkets.stream().map(CoinGeckoMarketDto::id).toList()
        ).stream().collect(Collectors.toMap(Crypto::getId, c -> c));
        List<Crypto> cryptos = new ArrayList<>(usdMarkets.size());
        for (CoinGeckoMarketDto usdDto : usdMarkets) {
            BigDecimal tryPrice = tryPriceMap.get(usdDto.id());
            Crypto existing = existingMap.get(usdDto.id());
            if (existing != null) {
                cryptoMapper.updateEntityFromDto(existing, usdDto, tryPrice, now);
                cryptos.add(existing);
            } else {
                cryptos.add(cryptoMapper.toEntity(usdDto, tryPrice, now));
            }
        }
        cryptoRepository.saveAll(cryptos);
        for (Crypto crypto : cryptos) {
            cryptoCacheService.clearSnapshotCache(crypto.getId());
        }
        log.info("Snapshot update completed: {} coins saved (USD + TRY)", cryptos.size());
    }
    public void updateOnlyCandles() {
        List<String> trackedCoins = marketConstants.getTrackedCryptos();
        log.info("Starting candle update with self-healing for {} coins...", trackedCoins.size());
        int processed = 0;
        int failed = 0;
        for (String coinId : trackedCoins) {
            try {
                long count = cryptoCandleRepository.countByCryptoId(coinId);
                if (count < MIN_CANDLES_FOR_HEALTHY) {
                    log.warn("Gap detected for {}. Reloading full history...", coinId);
                    reloadFullHistory(coinId);
                } else {
                    log.info("Data healthy for {}. Fetching today's update.", coinId);
                    updateDailyCandle(coinId);
                }
                processed++;
                cryptoCacheService.clearHistoryCache(coinId);
            } catch (Exception e) {
                failed++;
                log.error("Failed to fetch candle for {}: {}", coinId, e.getMessage());
            }
        }
        pruneOldCandles();
        log.info("Candle update completed: {} success, {} failed", processed, failed);
        if (failed > 0 && failed >= trackedCoins.size() / 2) {
            log.warn("HIGH FAILURE RATE: {} out of {} coins failed", failed, trackedCoins.size());
        }
    }
    public void fullMarketUpdate() {
        log.info("Starting FULL market update...");
        updateOnlySnapshots();
        updateOnlyCandles();
        log.info("FULL market update completed!");
    }
    private void reloadFullHistory(String coinId) {
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchMarketChartRange(coinId, HISTORY_DAYS);
        if (!dtos.isEmpty()) {
            Crypto crypto = cryptoRepository.getReferenceById(coinId);
            List<CryptoCandle> candles = dtos.stream()
                    .map(dto -> cryptoMapper.toCandleEntity(dto, crypto))
                    .toList();
            transactionTemplate.executeWithoutResult(status -> {
                cryptoCandleRepository.deleteByCryptoId(coinId);
                cryptoCandleRepository.saveAll(candles);
            });
            log.info("Reloaded {} candles for {}", candles.size(), coinId);
        }
    }
    private void updateDailyCandle(String coinId) {
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchDailyOhlc(coinId);
        for (CoinGeckoCandleDto dto : dtos) {
            LocalDateTime normalizedDate = dto.candleDate().truncatedTo(ChronoUnit.DAYS);
            Optional<CryptoCandle> existing = cryptoCandleRepository
                    .findByCryptoIdAndCandleDate(coinId, normalizedDate);
            if (existing.isPresent()) {
                cryptoMapper.updateCandleEntity(existing.get(), dto);
                cryptoCandleRepository.save(existing.get());
            } else {
                Crypto crypto = cryptoRepository.getReferenceById(coinId);
                cryptoCandleRepository.save(cryptoMapper.toCandleEntity(dto, crypto));
            }
        }
    }
    private void pruneOldCandles() {
        log.info("Pruning old candle data (keeping exactly {} days)...", HISTORY_DAYS);
        LocalDateTime cutoffDate = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(HISTORY_DAYS - 1);
        cryptoCandleRepository.deleteByCandleDateBefore(cutoffDate);
    }
}
