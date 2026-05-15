package com.finance.market.viop.model;

import java.util.Optional;

public enum ViopUnderlyingClass {
    PAY,
    INDEX,
    CURRENCY,
    METAL;

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
