package com.finance.market.viop.model;

import java.util.Optional;

/** Broad VIOP underlying class (equity/pay, index, currency, metal) grouping {@link ViopCategory}. */
public enum ViopUnderlyingClass {
    PAY,
    INDEX,
    CURRENCY,
    METAL;

    /** Case-insensitive parse, empty for unknown/blank names. */
    public static Optional<ViopUnderlyingClass> fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
