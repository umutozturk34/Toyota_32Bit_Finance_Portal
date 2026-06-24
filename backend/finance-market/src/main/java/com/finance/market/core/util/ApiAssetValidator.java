package com.finance.market.core.util;

import com.finance.common.exception.BusinessException;
import com.finance.common.exception.SymbolNotFoundException;
import com.finance.shared.util.CodeNormalizer;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

/**
 * Normalizes an asset code and runs an existence {@code lookup}. A blank code or a definitive
 * {@link SymbolNotFoundException} means "does not exist" (returns {@code false}); any other lookup failure is a
 * TRANSIENT upstream problem (timeout, 5xx, open circuit) and is surfaced as a temporarily-unavailable error
 * rather than collapsed to {@code false} — otherwise a valid asset added during an upstream hiccup would be
 * misreported to the user as "not found".
 */
public final class ApiAssetValidator {

    private ApiAssetValidator() {
    }

    /**
     * Returns whether the normalized code exists per {@code lookup}: {@code false} for a blank code or a
     * definitive not-found; throws {@link BusinessException} ({@code error.market.dataTemporarilyUnavailable})
     * when the lookup fails transiently, so the caller never confuses "upstream is down" with "does not exist".
     */
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
        } catch (SymbolNotFoundException e) {
            return false;
        } catch (RuntimeException e) {
            log.warn("{} existence check failed transiently for {}: {}", marketName, normalized, e.getMessage());
            throw new BusinessException("error.market.dataTemporarilyUnavailable", normalized);
        }
    }
}
