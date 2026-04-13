package com.finance.backend.service;

import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class CryptoDataService {

    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final CryptoSnapshotService cryptoSnapshotService;
    private final CryptoCandleService cryptoCandleService;

    public void validateCryptoExists(String coinId) {
        if (!cryptoSnapshotService.existsInApi(coinId)) {
            throw new BusinessException(
                    "Kripto varlık bulunamadı: " + coinId, "ASSET_NOT_FOUND");
        }
    }

    public void fullMarketUpdate() {
        updateOnlySnapshots();
        updateOnlyCandles();
    }

    public void updateOnlySnapshots() {
        cryptoSnapshotService.updateOnlySnapshots();
    }

    public void refreshTrackedCryptoSnapshot(String coinId) {
        cryptoSnapshotService.refreshTrackedCryptoSnapshot(coinId);
    }

    public void refreshTrackedCryptoCandles(String coinId) {
        cryptoCandleService.refreshTrackedCryptoCandles(coinId);
    }

    public void clearTrackedCryptoCache(String coinId) {
        String normalizedId = coinId == null ? "" : coinId.trim().toLowerCase();
        if (normalizedId.isBlank()) {
            return;
        }
        cryptoCacheService.clearCache(normalizedId);
        log.info("Cleared tracked crypto cache for {}", normalizedId);
    }

    public void updateOnlyCandles() {
        cryptoCandleService.updateOnlyCandles();
    }
}
