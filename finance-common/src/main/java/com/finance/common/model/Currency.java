package com.finance.common.model;

public enum Currency {
    TRY,
    USD,
    EUR;

    public static Currency fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (Currency c : values()) {
            if (c.name().equals(normalized)) {
                return c;
            }
        }
        return null;
    }

    public static Currency viopQuoteCurrencyOf(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return TRY;
        }
        String upper = symbol.trim().toUpperCase();
        if (upper.startsWith("O_")) {
            return TRY;
        }
        String base = upper.replaceAll("\\d{4}$", "");
        if (base.endsWith("USD")) {
            return USD;
        }
        if (base.endsWith("EUR")) {
            return EUR;
        }
        return TRY;
    }
}
