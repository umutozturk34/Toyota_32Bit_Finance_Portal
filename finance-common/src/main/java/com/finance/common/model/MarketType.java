package com.finance.common.model;

public enum MarketType {
    STOCK("Hisse"),
    CRYPTO("Kripto"),
    FOREX("Döviz"),
    FUND("Fon"),
    COMMODITY("Emtia");

    private final String displayLabel;

    MarketType(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }
}
