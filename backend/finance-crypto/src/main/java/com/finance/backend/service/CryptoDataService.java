package com.finance.backend.service;

import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class CryptoDataService implements TrackedAssetDataService {

    private final MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    private final CryptoSnapshotService cryptoSnapshotService;
    private final CryptoCandleService cryptoCandleService;

    @Override
    public TrackedAssetType getAssetType() {
        return TrackedAssetType.CRYPTO;
    }

    @Override
    public void validateExists(String coinId) {
        if (!cryptoSnapshotService.existsInApi(coinId)) {
            throw new BusinessException(
                    "Kripto varlık bulunamadı: " + coinId, "ASSET_NOT_FOUND");
        }
    }

    @Override
    public void refreshSnapshot(String coinId) {
        cryptoSnapshotService.refreshSnapshot(coinId);
    }

    @Override
    public void refreshCandles(String coinId) {
        cryptoCandleService.refreshCandles(coinId);
    }

    @Override
    public void refreshAllSnapshots() {
        cryptoSnapshotService.refreshAll();
    }

    @Override
    public void refreshAllCandles() {
        cryptoCandleService.refreshAll();
    }

    @Override
    public void clearCache(String coinId) {
        MarketAssetCacheHelper.clearIfValid(coinId, cryptoCacheService, false, log, "crypto");
    }
}
