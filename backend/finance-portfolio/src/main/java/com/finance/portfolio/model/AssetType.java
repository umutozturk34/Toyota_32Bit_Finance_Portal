package com.finance.portfolio.model;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;

/**
 * Asset classes a portfolio can hold, each mapped 1:1 to a market-data {@link MarketType} so pricing
 * lookups can be routed. {@code VIOP} covers derivative (future/option) positions.
 */
public enum AssetType {
    CRYPTO(MarketType.CRYPTO),
    STOCK(MarketType.STOCK),
    FOREX(MarketType.FOREX),
    FUND(MarketType.FUND),
    COMMODITY(MarketType.COMMODITY),
    VIOP(MarketType.VIOP),
    DEPOSIT(MarketType.MACRO_DEPOSIT),
    BOND(MarketType.BOND);

    private final MarketType marketType;

    AssetType(MarketType marketType) {
        this.marketType = marketType;
    }

    /** The market-data {@link MarketType} this asset class maps to, used to route pricing lookups. */
    public MarketType marketType() {
        return marketType;
    }

    /**
     * Whether this class trades in whole units (adet): true for stocks and funds, which cannot be held
     * fractionally. Crypto/forex/commodity are fractional, and VIOP lots are sized by the derivative flow,
     * so they are excluded. Mirrors the frontend {@code FRACTIONAL_TYPES} distinction.
     */
    public boolean isWholeUnitOnly() {
        return this == STOCK || this == FUND;
    }

    /**
     * The curated {@link TrackedAssetType} peer for this class, or {@code null} when none exists. DEPOSIT and
     * BOND map to a {@link MarketType} but have NO {@code TrackedAssetType} (they live in their own fixed-income
     * tables, not the spot watchlist), and VIOP routes through derivative valuation rather than a tracked asset.
     * Callers that need a tracked asset MUST use this safe lookup rather than {@code TrackedAssetType.valueOf(name())},
     * which would throw {@link IllegalArgumentException} for exactly those classes and crash the request.
     */
    public TrackedAssetType trackedAssetType() {
        return TrackedAssetType.fromMarketType(marketType);
    }

    /** Reverse lookup from a market type; null when no asset class maps to it. */
    public static AssetType fromMarketType(MarketType marketType) {
        for (AssetType type : values()) {
            if (type.marketType == marketType) {
                return type;
            }
        }
        return null;
    }
}
