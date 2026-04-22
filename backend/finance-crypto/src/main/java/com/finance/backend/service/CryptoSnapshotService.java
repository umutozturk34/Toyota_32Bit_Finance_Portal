package com.finance.backend.service;

import com.finance.backend.client.CoinGeckoClient;
import com.finance.backend.dto.external.CoinGeckoSnapshotDto;
import com.finance.backend.mapper.CryptoMapper;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CryptoRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CodeNormalizer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class CryptoSnapshotService implements SnapshotBatchRefresher {

    private final CoinGeckoClient coinGeckoClient;
    private final CryptoMapper cryptoMapper;
    private final CryptoRepository cryptoRepository;
    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;

    public boolean existsInApi(String coinId) {
        String normalized = CodeNormalizer.lower(coinId);
        if (normalized.isBlank()) return false;
        try {
            List<CoinGeckoSnapshotDto> result = coinGeckoClient.fetchMarkets("usd", List.of(normalized));
            return !result.isEmpty();
        } catch (Exception e) {
            log.warn("Crypto existence check failed for {}: {}", normalized, e.getMessage());
            return false;
        }
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.CRYPTO;
    }

    @Override
    public void refreshAll() {
        List<String> trackedCoins = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.CRYPTO);
        log.info("Starting crypto snapshot update for {} coins", trackedCoins.size());
        List<CoinGeckoSnapshotDto> usdMarkets = coinGeckoClient.fetchMarkets("usd", trackedCoins);
        List<CoinGeckoSnapshotDto> tryMarkets = coinGeckoClient.fetchMarkets("try", trackedCoins);
        Map<String, BigDecimal> tryPriceMap = tryMarkets.stream()
                .collect(Collectors.toMap(CoinGeckoSnapshotDto::id, CoinGeckoSnapshotDto::currentPrice));

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                usdMarkets,
                usdDto -> {
                    BigDecimal tryPrice = tryPriceMap.get(usdDto.id());
                    Crypto saved = transactionTemplate.execute(status -> saveSingleSnapshot(usdDto, tryPrice));
                    cryptoCacheService.putSnapshot(saved.getId(), saved);
                },
                CoinGeckoSnapshotDto::id,
                "snapshot",
                5,
                (usdDto, e) -> log.error("Failed to save snapshot for {}: {}", usdDto.id(), e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("Crypto snapshot batch stopped early (circuit breaker open): {} success, {} failed",
                        stopped.successCount(), stopped.failCount()));

        BatchLogHelper.logSummary(log, "Crypto snapshot update", result);
    }

    public void refreshTrackedCryptoSnapshot(String coinId) {
        String normalizedId = CodeNormalizer.lower(coinId);
        if (normalizedId.isBlank()) {
            return;
        }

        List<CoinGeckoSnapshotDto> usdMarkets = coinGeckoClient.fetchMarkets("usd", List.of(normalizedId));
        if (usdMarkets.isEmpty()) {
            log.warn("No USD snapshot found for tracked crypto {}", normalizedId);
            return;
        }

        List<CoinGeckoSnapshotDto> tryMarkets = coinGeckoClient.fetchMarkets("try", List.of(normalizedId));
        BigDecimal tryPrice = tryMarkets.isEmpty() ? null : tryMarkets.getFirst().currentPrice();

        Crypto saved = transactionTemplate.execute(status -> saveSingleSnapshot(usdMarkets.getFirst(), tryPrice));
        cryptoCacheService.putSnapshot(saved.getId(), saved);
        log.info("Refreshed tracked crypto snapshot for {}", normalizedId);
    }

    private Crypto saveSingleSnapshot(CoinGeckoSnapshotDto usdDto, BigDecimal tryPrice) {
        LocalDateTime now = LocalDateTime.now();
        Crypto existing = cryptoRepository.findById(usdDto.id()).orElse(null);
        Crypto toPersist;
        if (existing != null) {
            cryptoMapper.updateEntityFromDto(existing, usdDto, tryPrice, now);
            toPersist = existing;
        } else {
            toPersist = cryptoMapper.toEntity(usdDto, tryPrice, now);
        }
        return cryptoRepository.save(toPersist);
    }
}
