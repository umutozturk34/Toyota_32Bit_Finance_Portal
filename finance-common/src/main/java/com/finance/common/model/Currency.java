package com.finance.common.model;

/**
 * Supported quote/settlement currencies across the platform.
 */
public enum Currency {
    TRY,
    USD,
    EUR;

    /**
     * Parses a currency code case-insensitively, returning {@code null} for an unknown or null code.
     */
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

    /**
     * Derives the currency a VIOP (Turkish derivatives) price is quoted in from its symbol.
     * Options ({@code O_} prefix) are always TRY. For futures ({@code F_}) the trailing 4-digit
     * expiry (MMYY) is stripped, then a trailing {@code USD}/{@code EUR} token selects that
     * currency; otherwise TRY. The stored exchange-currency field must not be used for FX, as it
     * does not reflect the quote currency.
     */
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
