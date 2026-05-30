package com.finance.shared.util;

/**
 * Canonicalizes asset/symbol codes for consistent storage and lookup: trims and case-folds,
 * mapping null to an empty string so callers never deal with null codes.
 */
public final class CodeNormalizer {

    private CodeNormalizer() {
    }

    public static String upper(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    public static String lower(String code) {
        return code == null ? "" : code.trim().toLowerCase();
    }
}
