package com.finance.common.model;

/**
 * Catalog of market segments. Each constant carries the metadata needed to read a snapshot from
 * Redis: {@code redisLabel} (the key segment used to build {@code market:<label>:snapshot:<code>}),
 * {@code codeField} (the JSON property holding the asset code), and {@code primaryPriceField} with an
 * optional {@code fallbackPriceField} read when the primary is absent. These field names are
 * market-specific and reflect the JSON shape each provider publishes.
 */
public enum MarketType {
    STOCK("stock", "symbol", "currentPrice", null),
    CRYPTO("crypto", "id", "currentPriceTry", null),
    FOREX("forex", "currencyCode", "sellingPrice", "currentPrice"),
    FUND("fund", "fundCode", "price", null),
    COMMODITY("commodity", "commodityCode", "currentPrice", null),
    BOND("bond", "code", "currentPrice", null),
    VIOP("viop", "symbol", "lastPrice", "dayClose"),
    MACRO_RATE("macro_rate", "code", "lastValue", null),
    MACRO_INFLATION("macro_inflation", "code", "lastValue", null),
    MACRO_DEPOSIT("macro_deposit", "code", "lastValue", null);

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
