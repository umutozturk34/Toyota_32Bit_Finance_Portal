package com.finance.shared.util;

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
