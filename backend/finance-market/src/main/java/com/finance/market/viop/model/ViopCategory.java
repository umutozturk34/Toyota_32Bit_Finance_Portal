package com.finance.market.viop.model;

import java.util.Arrays;
import java.util.List;

public enum ViopCategory {
    PAY_FUTURE(ViopUnderlyingClass.PAY),
    INDEX_FUTURE(ViopUnderlyingClass.INDEX),
    CURRENCY_FUTURE_TRY(ViopUnderlyingClass.CURRENCY),
    CURRENCY_FUTURE_USD(ViopUnderlyingClass.CURRENCY),
    METAL_FUTURE_TRY(ViopUnderlyingClass.METAL),
    METAL_FUTURE_USD(ViopUnderlyingClass.METAL),
    METAL_FUTURE(ViopUnderlyingClass.METAL),
    PAY_OPTION(ViopUnderlyingClass.PAY),
    INDEX_OPTION(ViopUnderlyingClass.INDEX),
    CURRENCY_OPTION(ViopUnderlyingClass.CURRENCY);

    private final ViopUnderlyingClass underlyingClass;

    ViopCategory(ViopUnderlyingClass underlyingClass) {
        this.underlyingClass = underlyingClass;
    }

    public ViopUnderlyingClass underlyingClass() {
        return underlyingClass;
    }

    public static List<ViopCategory> resolveFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.trim().toUpperCase();
        List<ViopCategory> exact = Arrays.stream(values())
                .filter(c -> c.name().equals(normalized))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return ViopUnderlyingClass.fromName(normalized)
                .map(uc -> Arrays.stream(values())
                        .filter(c -> c.underlyingClass == uc)
                        .toList())
                .orElseGet(List::of);
    }
}
