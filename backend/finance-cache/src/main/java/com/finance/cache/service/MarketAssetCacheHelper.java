package com.finance.cache.service;
import com.finance.cache.service.MarketAssetCacheHelper;

import com.finance.cache.service.MarketCacheService;


import com.finance.common.util.CodeNormalizer;
import org.apache.logging.log4j.Logger;

public final class MarketAssetCacheHelper {

    private MarketAssetCacheHelper() {
    }

    public static <T, C> void clearIfValid(String code,
                                           MarketCacheService<T> cache,
                                           boolean toUpper,
                                           Logger log,
                                           String marketName) {
        String normalized = toUpper ? CodeNormalizer.upper(code) : CodeNormalizer.lower(code);
        if (normalized.isBlank()) {
            return;
        }
        cache.clearCache(normalized);
        log.info("Cleared tracked {} cache for {}", marketName, normalized);
    }
}
