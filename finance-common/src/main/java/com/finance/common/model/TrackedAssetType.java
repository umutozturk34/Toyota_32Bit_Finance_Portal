package com.finance.common.model;

import java.util.Locale;

/**
 * Asset categories on the watchlist, each bound to a {@link MarketType} and supplying type-specific
 * normalization/resolution rules via overridable strategy methods. Code normalization differs by
 * type (crypto lowercases, the rest uppercase) and only some types resolve a Binance symbol or
 * stock-segment-derived flags; the base implementations provide the inert defaults.
 */
public enum TrackedAssetType {
    CRYPTO(MarketType.CRYPTO) {
        @Override
        public String normalizeCode(String raw) {
            return ensureNonBlank(raw).toLowerCase(Locale.ROOT);
        }

        @Override
        public String resolveBinanceSymbol(String requested) {
            if (requested == null || requested.isBlank()) return null;
            return requested.trim().toUpperCase(Locale.ROOT);
        }
    },
    STOCK(MarketType.STOCK) {
        @Override
        public String normalizeCode(String raw) {
            return ensureNonBlank(raw).toUpperCase(Locale.ROOT);
        }

        @Override
        public StockSegment resolveSegment(StockSegment requested, StockSegment existing) {
            if (requested != null) return requested;
            if (existing != null) return existing;
            return StockSegment.EQUITY;
        }

        @Override
        public boolean resolveIndexAsset(StockSegment segment, Boolean requested, boolean existing) {
            if (requested != null) return requested;
            if (segment != null && segment != StockSegment.EQUITY) return true;
            return existing;
        }

        @Override
        public boolean resolveCompareOnly(StockSegment segment, Boolean requested, boolean existing) {
            if (requested != null) return requested;
            if (segment == StockSegment.SECONDARY_INDEX) return true;
            return existing;
        }
    },
    FUND(MarketType.FUND) {
        @Override
        public String normalizeCode(String raw) {
            return ensureNonBlank(raw).toUpperCase(Locale.ROOT);
        }
    },
    COMMODITY(MarketType.COMMODITY) {
        @Override
        public String normalizeCode(String raw) {
            return ensureNonBlank(raw).toUpperCase(Locale.ROOT);
        }
    },
    FOREX(MarketType.FOREX) {
        @Override
        public String normalizeCode(String raw) {
            return ensureNonBlank(raw).toUpperCase(Locale.ROOT);
        }
    },
    VIOP(MarketType.VIOP) {
        @Override
        public String normalizeCode(String raw) {
            return ensureNonBlank(raw).toUpperCase(Locale.ROOT);
        }
    };

    private final MarketType marketType;

    TrackedAssetType(MarketType marketType) {
        this.marketType = marketType;
    }

    public MarketType marketType() {
        return marketType;
    }

    /** The tracked-asset type backing a {@link MarketType}, or {@code null} for market types that are not curated here. */
    public static TrackedAssetType fromMarketType(MarketType marketType) {
        if (marketType == null) {
            return null;
        }
        for (TrackedAssetType type : values()) {
            if (type.marketType == marketType) {
                return type;
            }
        }
        return null;
    }

    /**
     * Canonicalizes a raw asset code for this type, throwing
     * {@link com.finance.common.exception.BadRequestException} when blank.
     */
    public abstract String normalizeCode(String raw);

    /**
     * Maps a requested code to its Binance trading symbol, or {@code null} when not applicable
     * (only crypto overrides this).
     */
    public String resolveBinanceSymbol(String requested) {
        return null;
    }

    /**
     * Resolves the effective stock segment, preferring the requested value, then the existing one,
     * defaulting to {@link StockSegment#EQUITY}; {@code null} for non-stock types.
     */
    public StockSegment resolveSegment(StockSegment requested, StockSegment existing) {
        return null;
    }

    /**
     * Resolves the index-membership flag: an explicit request wins, otherwise any non-equity
     * segment implies index membership, else the existing value is kept.
     */
    public boolean resolveIndexAsset(StockSegment segment, Boolean requested, boolean existing) {
        return false;
    }

    /**
     * Resolves the compare-only flag: an explicit request wins, otherwise a secondary-index segment
     * forces compare-only, else the existing value is kept.
     */
    public boolean resolveCompareOnly(StockSegment segment, Boolean requested, boolean existing) {
        return false;
    }

    protected static String ensureNonBlank(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new com.finance.common.exception.BadRequestException("error.assetCode.blank");
        }
        return trimmed;
    }
}
