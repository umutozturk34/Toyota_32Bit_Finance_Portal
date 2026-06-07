package com.finance.market.macro.model;

import com.finance.common.model.MarketType;

/** Macro indicator grouping (rates, inflation, deposit) mapped to its {@link MarketType}. */
public enum MacroCategory {
    RATES(MarketType.MACRO_RATE),
    INFLATION(MarketType.MACRO_INFLATION),
    DEPOSIT(MarketType.MACRO_DEPOSIT);

    private final MarketType instrumentType;

    MacroCategory(MarketType instrumentType) {
        this.instrumentType = instrumentType;
    }

    /** The {@link MarketType} this category's indicators are persisted/queried as. */
    public MarketType instrumentType() {
        return instrumentType;
    }
}
