package com.finance.backend.service;

import org.apache.logging.log4j.Logger;

public final class MarketAssetCacheHelper {

    private MarketAssetCacheHelper() {
    }

    public static <T, C> void clearIfValid(String code,
                                           MarketCacheService<T, C> cache,
                                           boolean toUpper,
                                           Logger log,
                                           String marketName) {
        String normalized = normalizeCode(code, toUpper);
        if (normalized.isBlank()) {
            return;
        }
        cache.clearCache(normalized);
        log.info("Cleared tracked {} cache for {}", marketName, normalized);
    }

    public static String normalizeCode(String code, boolean toUpper) {
        if (code == null) return "";
        String trimmed = code.trim();
        return toUpper ? trimmed.toUpperCase() : trimmed.toLowerCase();
    }
}
