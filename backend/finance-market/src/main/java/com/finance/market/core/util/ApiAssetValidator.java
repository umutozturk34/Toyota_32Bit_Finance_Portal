package com.finance.market.core.util;

import com.finance.shared.util.CodeNormalizer;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

/**
 * Normalizes an asset code and runs an existence {@code lookup}, treating blank codes and lookup
 * exceptions as "does not exist" so validation never throws to the caller.
 */
public final class ApiAssetValidator {

    private ApiAssetValidator() {
    }

    /** Returns whether the normalized code exists per {@code lookup}; false on blank or lookup error. */
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
