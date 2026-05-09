package com.finance.market.core.cache;
import com.finance.market.core.cache.MarketAssetCacheHelper;



import com.finance.market.core.model.BaseAsset;
import com.finance.shared.util.CodeNormalizer;
import org.apache.logging.log4j.Logger;

public final class MarketAssetCacheHelper {

    private MarketAssetCacheHelper() {
    }

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
