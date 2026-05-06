package com.finance.common.util;

import org.apache.logging.log4j.Logger;

import java.util.function.Function;

public final class ApiAssetValidator {

    private ApiAssetValidator() {
    }

    public static boolean validate(String code,
                                   boolean toUpper,
                                   Function<String, Boolean> lookup,
                                   Logger log,
                                   String marketName) {
        String normalized = toUpper ? CodeNormalizer.upper(code) : CodeNormalizer.lower(code);
        if (normalized.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(lookup.apply(normalized));
        } catch (Exception e) {
            log.warn("{} existence check failed for {}: {}", marketName, normalized, e.getMessage());
            return false;
        }
    }
}
