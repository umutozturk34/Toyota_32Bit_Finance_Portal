package com.finance.market.crypto.service;
import com.finance.market.core.cache.MarketAssetCacheHelper;

import com.finance.market.core.service.TrackedAssetDataService;

import com.finance.market.core.cache.MarketCacheService;


import com.finance.common.exception.BusinessException;
import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.crypto.model.Crypto;
import com.finance.common.model.TrackedAssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Crypto {@link TrackedAssetDataService}: validates a coin (via CoinGecko id and Binance symbol)
 * before tracking and delegates refresh/cache work to the update service and cache.
 */
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
    public void validateExists(TrackedAssetUpsertCommand command) {
        String coinId = command.getAssetCode();
        String binanceSymbol = command.getBinanceSymbol();
        if (!cryptoUpdateService.exists(coinId, binanceSymbol)) {
            throw new BusinessException("error.market.cryptoNotFound", coinId);
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
