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
}
