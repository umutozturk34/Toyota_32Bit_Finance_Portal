package com.finance.backend.model;

import java.util.Locale;

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
    };

    private final MarketType marketType;

    TrackedAssetType(MarketType marketType) {
        this.marketType = marketType;
    }

    public MarketType marketType() {
        return marketType;
    }

    public abstract String normalizeCode(String raw);

    public String resolveBinanceSymbol(String requested) {
        return null;
    }

    public StockSegment resolveSegment(StockSegment requested, StockSegment existing) {
        return null;
    }

    public boolean resolveIndexAsset(StockSegment segment, Boolean requested, boolean existing) {
        return false;
    }

    public boolean resolveCompareOnly(StockSegment segment, Boolean requested, boolean existing) {
        return false;
    }

    protected static String ensureNonBlank(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Asset code cannot be blank");
        }
        return trimmed;
    }
}
