package com.finance.market.core.cache;

import com.finance.market.core.model.BaseAsset;
import com.finance.shared.util.CodeNormalizer;
import org.apache.logging.log4j.Logger;

/** Normalizes a code and evicts its snapshot from the market cache, skipping blank codes. */
public final class MarketAssetCacheHelper {

    private MarketAssetCacheHelper() {
    }

    /** Clears the cache entry for the normalized code; no-op when the code is blank. */
    public static <T extends BaseAsset> void clearIfValid(String code,
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
