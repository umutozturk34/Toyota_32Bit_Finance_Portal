package com.finance.market.macro.model;

import com.finance.common.model.MarketType;

public enum MacroCategory {
    RATES(MarketType.MACRO_RATE),
    INFLATION(MarketType.MACRO_INFLATION),
    DEPOSIT(MarketType.MACRO_DEPOSIT);

    private final MarketType instrumentType;

    MacroCategory(MarketType instrumentType) {
        this.instrumentType = instrumentType;
    }

    public MarketType instrumentType() {
        return instrumentType;
    }
}
