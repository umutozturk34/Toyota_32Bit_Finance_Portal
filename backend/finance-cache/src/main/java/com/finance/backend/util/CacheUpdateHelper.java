package com.finance.backend.util;

import com.finance.backend.service.MarketCacheService;

public final class CacheUpdateHelper {

    private CacheUpdateHelper() {
    }

    public static <T, C> void putSnapshot(MarketCacheService<T, C> cacheService, String key, T snapshot) {
        cacheService.putSnapshot(key, snapshot);
    }

    public static <T, C> void refreshHistory(MarketCacheService<T, C> cacheService, String key) {
        cacheService.refreshHistory(key);
    }

    public static <T, C> void putSnapshotAndRefreshHistory(
            MarketCacheService<T, C> cacheService,
            String key,
            T snapshot) {
        cacheService.putSnapshot(key, snapshot);
        cacheService.refreshHistory(key);
    }
}
