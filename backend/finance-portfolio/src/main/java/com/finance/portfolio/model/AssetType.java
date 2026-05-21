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

    public static AssetType fromMarketType(MarketType marketType) {
        for (AssetType type : values()) {
            if (type.marketType == marketType) {
                return type;
            }
        }
        return null;
    }
}
