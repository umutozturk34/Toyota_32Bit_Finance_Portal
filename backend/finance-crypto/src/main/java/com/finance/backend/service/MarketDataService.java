package com.finance.backend.service;
import com.finance.backend.client.CoinGeckoClient;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoMarketDto;
import com.finance.backend.mapper.CryptoMapper;
import com.finance.backend.util.BatchFailureGuard;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.constants.MarketConstants;
import com.finance.backend.repository.CryptoCandleRepository;
import com.finance.backend.repository.CryptoRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Lazy;
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
@Log4j2
@Service
public class MarketDataService {
    private static final int HISTORY_DAYS = 365;
    private static final int MIN_CANDLES_FOR_HEALTHY = 350;
    private final CoinGeckoClient coinGeckoClient;
    private final CryptoMapper cryptoMapper;
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final MarketConstants marketConstants;
    private final TransactionTemplate transactionTemplate;
    private final MarketDataService self;
    public MarketDataService(CoinGeckoClient coinGeckoClient,
                             CryptoMapper cryptoMapper,
                             CryptoRepository cryptoRepository,
                             CryptoCandleRepository cryptoCandleRepository,
                             MarketCacheService<Crypto, CryptoCandle> cryptoCacheService,
                             MarketConstants marketConstants,
                             PlatformTransactionManager transactionManager,
                             @Lazy MarketDataService self) {
        this.coinGeckoClient = coinGeckoClient;
        this.cryptoMapper = cryptoMapper;
        this.cryptoRepository = cryptoRepository;
        this.cryptoCandleRepository = cryptoCandleRepository;
        this.cryptoCacheService = cryptoCacheService;
        this.marketConstants = marketConstants;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.self = self;
    }
    public void updateOnlySnapshots() {
        List<String> trackedCoins = marketConstants.getTrackedCryptos();
        List<CoinGeckoMarketDto> usdMarkets = coinGeckoClient.fetchMarkets("usd", trackedCoins);
        List<CoinGeckoMarketDto> tryMarkets = coinGeckoClient.fetchMarkets("try", trackedCoins);
        Map<String, BigDecimal> tryPriceMap = tryMarkets.stream()
                .collect(Collectors.toMap(CoinGeckoMarketDto::id, CoinGeckoMarketDto::currentPrice));

        int successCount = 0;
        int failCount = 0;
        List<String> failedIds = new ArrayList<>();
        for (CoinGeckoMarketDto usdDto : usdMarkets) {
            try {
                BigDecimal tryPrice = tryPriceMap.get(usdDto.id());
                Crypto saved = self.saveSingleSnapshot(usdDto, tryPrice);
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

    @org.springframework.transaction.annotation.Transactional
    public Crypto saveSingleSnapshot(CoinGeckoMarketDto usdDto, BigDecimal tryPrice) {
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
        int processed = 0;
        int failed = 0;
        List<String> failedCoins = new ArrayList<>();
        for (String coinId : trackedCoins) {
            try {
                long count = cryptoCandleRepository.countByCryptoId(coinId);
                if (count < MIN_CANDLES_FOR_HEALTHY) {
                    reloadFullHistory(coinId);
                } else {
                    updateDailyCandle(coinId);
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
        self.updateOnlySnapshots();
        self.updateOnlyCandles();
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
        }
    }
    private void updateDailyCandle(String coinId) {
        List<CoinGeckoCandleDto> dtos = coinGeckoClient.fetchDailyOhlc(coinId);
        transactionTemplate.executeWithoutResult(status -> {
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
        });
    }
    private void pruneOldCandles() {
        LocalDateTime cutoffDate = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(HISTORY_DAYS - 1);
        transactionTemplate.executeWithoutResult(status -> {
            cryptoCandleRepository.deleteByCandleDateBefore(cutoffDate);
        });
    }
}
