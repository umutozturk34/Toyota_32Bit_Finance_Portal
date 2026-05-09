package com.finance.common.model;

public enum MarketType {
    STOCK("Hisse", "stock", "symbol", "currentPrice", null),
    CRYPTO("Kripto", "crypto", "id", "currentPriceTry", null),
    FOREX("Döviz", "forex", "currencyCode", "sellingPrice", "currentPrice"),
    FUND("Fon", "fund", "fundCode", "price", null),
    COMMODITY("Emtia", "commodity", "commodityCode", "currentPrice", null),
    BOND("Tahvil", "bond", "code", "currentPrice", null);

    private final String displayLabel;
    private final String redisLabel;
    private final String codeField;
    private final String primaryPriceField;
    private final String fallbackPriceField;

    MarketType(String displayLabel, String redisLabel, String codeField,
               String primaryPriceField, String fallbackPriceField) {
        this.displayLabel = displayLabel;
        this.redisLabel = redisLabel;
        this.codeField = codeField;
        this.primaryPriceField = primaryPriceField;
        this.fallbackPriceField = fallbackPriceField;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public String redisLabel() {
        return redisLabel;
    }

    public String codeField() {
        return codeField;
    }

    public String primaryPriceField() {
        return primaryPriceField;
    }

    public String fallbackPriceField() {
        return fallbackPriceField;
    }
}
