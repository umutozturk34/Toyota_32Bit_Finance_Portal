package com.finance.app.analytics.dto;

import com.finance.common.model.MarketType;

/**
 * Instrument families the analytics engine understands. Each carries a {@link MarketType} when backed by
 * a market price series (null for rate/macro-backed kinds) and a {@link Kind} flagging whether it follows
 * a PRICE path or compounds as a RATE — which drives how the scenario engine values it.
 */
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

    /** True when this type has a market price series (and thus a non-null {@link MarketType}). */
    public boolean isMarketBacked() {
        return marketType != null;
    }

    /** True when this type represents a rate/yield that should compound rather than track a price. */
    public boolean isRateBacked() {
        return kind == Kind.RATE;
    }
}
