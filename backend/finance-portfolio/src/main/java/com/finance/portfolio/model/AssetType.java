package com.finance.portfolio.model;
import com.finance.common.model.MarketType;

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
    VIOP(MarketType.VIOP);

    private final MarketType marketType;

    AssetType(MarketType marketType) {
        this.marketType = marketType;
    }

    /** The market-data {@link MarketType} this asset class maps to, used to route pricing lookups. */
    public MarketType marketType() {
        return marketType;
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
