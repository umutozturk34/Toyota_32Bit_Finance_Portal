package com.finance.app.analytics.dto;

import com.finance.common.model.MarketType;

public enum AnalyticsInstrumentType {
    SPOT(MarketType.STOCK, Kind.PRICE),
    VIOP(MarketType.VIOP, Kind.PRICE),
    FUND(MarketType.FUND, Kind.PRICE),
    FOREX(MarketType.FOREX, Kind.PRICE),
    CRYPTO(MarketType.CRYPTO, Kind.PRICE),
    COMMODITY(MarketType.COMMODITY, Kind.PRICE),
    BOND(null, Kind.RATE),
    MACRO(null, Kind.RATE),
    DEPOSIT(null, Kind.RATE),
    PORTFOLIO(null, Kind.PRICE);

    public enum Kind {
        PRICE,
        RATE
    }

    private final MarketType marketType;
    private final Kind kind;

    AnalyticsInstrumentType(MarketType marketType, Kind kind) {
        this.marketType = marketType;
        this.kind = kind;
    }

    public MarketType marketType() {
        return marketType;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isMarketBacked() {
        return marketType != null;
    }

    public boolean isRateBacked() {
        return kind == Kind.RATE;
    }
}
