package com.finance.notification.market;

public enum SessionMarket {
    STOCK("Hisse"),
    FOREX("Döviz"),
    FUND("Fon"),
    COMMODITY("Emtia"),
    BOND("Tahvil"),
    NEWS("Haberler"),
    CRYPTO("Kripto");

    private final String displayLabel;

    SessionMarket(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }
}
