package com.finance.common.model;

public enum MarketType {
    STOCK("stock", "symbol", "currentPrice", null),
    CRYPTO("crypto", "id", "currentPriceTry", null),
    FOREX("forex", "currencyCode", "sellingPrice", "currentPrice"),
    FUND("fund", "fundCode", "price", null),
    COMMODITY("commodity", "commodityCode", "currentPrice", null),
    BOND("bond", "code", "currentPrice", null);

    private final String redisLabel;
    private final String codeField;
    private final String primaryPriceField;
    private final String fallbackPriceField;

    MarketType(String redisLabel, String codeField,
               String primaryPriceField, String fallbackPriceField) {
        this.redisLabel = redisLabel;
        this.codeField = codeField;
        this.primaryPriceField = primaryPriceField;
        this.fallbackPriceField = fallbackPriceField;
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
