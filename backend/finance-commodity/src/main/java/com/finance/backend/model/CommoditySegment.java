package com.finance.backend.model;

import java.util.Set;

public enum CommoditySegment {
    PRECIOUS_METAL,
    OTHER;

    private static final Set<String> PRECIOUS_BASE = Set.of("GC=F", "SI=F", "PL=F", "PA=F");

    public static CommoditySegment fromCode(String code) {
        if (code == null) return null;
        if (PRECIOUS_BASE.contains(code)) return PRECIOUS_METAL;
        if (code.startsWith("GOLD_") || code.startsWith("SILVER_")) return PRECIOUS_METAL;
        if (code.contains("=F")) return OTHER;
        return null;
    }
}
