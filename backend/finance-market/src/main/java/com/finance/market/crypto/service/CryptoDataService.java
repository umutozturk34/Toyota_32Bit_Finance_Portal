package com.finance.market.crypto.service;
import com.finance.cache.service.MarketAssetCacheHelper;

import com.finance.common.service.TrackedAssetDataService;

import com.finance.cache.service.MarketCacheService;


import com.finance.common.exception.BusinessException;
import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.model.CryptoCandle;
import com.finance.common.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class CryptoDataService implements TrackedAssetDataService {

    private final MarketCacheService<Crypto> cryptoCacheService;
    private final CryptoUpdateService cryptoUpdateService;

    @Override
    public TrackedAssetType getAssetType() {
        return TrackedAssetType.CRYPTO;
    }

    @Override
    public void validateExists(String coinId) {
        if (!cryptoUpdateService.exists(coinId)) {
            throw new BusinessException(
                    "Kripto varlık bulunamadı: " + coinId, "ASSET_NOT_FOUND");
        }
    }

    @Override
    public void refresh(String coinId) {
        cryptoUpdateService.refresh(coinId);
    }

    @Override
    public void refreshAll() {
        cryptoUpdateService.refreshAll();
    }

    @Override
    public void clearCache(String coinId) {
        MarketAssetCacheHelper.clearIfValid(coinId, cryptoCacheService, false, log, "crypto");
    }
}
