package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.response.AssetReturnsResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * In-app Caffeine cache for the single asset-returns dataset (24h TTL). The dataset is one object (all
 * assets × all windows), so it lives under one fixed key and only a non-empty result is retained. Caffeine's
 * atomic {@code get(key, loader)} collapses concurrent misses into a single computation, so no extra
 * in-flight guard is needed. Mirrors {@link BeaterCacheManager} for the returns view.
 */
@Log4j2
@Component
public class AssetReturnsCacheManager {

    private static final String KEY = "asset-returns";

    private final Cache<String, AssetReturnsResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    /** Returns the cached dataset or computes it once; an empty (cold-start) result is returned but not retained. */
    public AssetReturnsResponse getOrCompute(Supplier<AssetReturnsResponse> loader) {
        AssetReturnsResponse response = cache.get(KEY, k -> loader.get());
        if (!isWorthCaching(response)) {
            cache.invalidate(KEY);
        }
        return response;
    }

    /** Recomputes and stores the dataset (startup/daily warm-up); an empty result is not cached. */
    public void refresh(Supplier<AssetReturnsResponse> loader) {
        AssetReturnsResponse result = loader.get();
        if (isWorthCaching(result)) {
            cache.put(KEY, result);
        } else {
            log.info("Asset-returns refresh produced an empty dataset, not caching");
        }
    }

    /** Cached dataset without computing; null on a cold cache (caller may trigger an async warm). */
    public AssetReturnsResponse peek() {
        return cache.getIfPresent(KEY);
    }

    public void clear() {
        cache.invalidateAll();
    }

    public boolean isWorthCaching(AssetReturnsResponse r) {
        return r != null && r.assets() != null && !r.assets().isEmpty();
    }
}
