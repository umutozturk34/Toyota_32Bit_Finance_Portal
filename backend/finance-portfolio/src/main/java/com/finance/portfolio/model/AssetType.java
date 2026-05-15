package com.finance.portfolio.model;
import com.finance.common.model.MarketType;

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

    public MarketType marketType() {
        return marketType;
    }

    /**
     * Resolves the spot {@link AssetType} backing a market type, or {@code null} when the market
     * type has no spot-position representation (e.g. VIOP derivatives, which live in their own
     * {@code DerivativePosition} model rather than {@code PortfolioPosition}).
     */
    public static AssetType fromMarketType(MarketType marketType) {
        for (AssetType type : values()) {
            if (type.marketType == marketType) {
                return type;
            }
        }
        return null;
    }
}
