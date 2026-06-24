package com.finance.market.core.util;

import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.exception.SymbolNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ApiAssetValidator}: a blank code or a definitive not-found is "does not exist", but a
 * transient upstream failure must surface as temporarily-unavailable rather than being misreported as not-found.
 */
class ApiAssetValidatorTest {

    private static final Logger LOG = LogManager.getLogger(ApiAssetValidatorTest.class);

    @Test
    void returnsTrue_whenLookupConfirmsExistence() {
        // Arrange + Act
        boolean exists = ApiAssetValidator.validate("aapl", true, code -> true, LOG, "Stock");

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    void returnsFalse_whenLookupReportsAbsentByReturningFalse() {
        // Arrange + Act: crypto/fund signal a genuine not-found by returning false (empty result), no exception.
        boolean exists = ApiAssetValidator.validate("nope", true, code -> false, LOG, "Crypto");

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    void returnsFalse_whenSymbolNotFoundThrown() {
        // Arrange + Act: a Yahoo 404 → SymbolNotFoundException is a definitive "does not exist", not an error.
        boolean exists = ApiAssetValidator.validate("ghost", true,
                code -> { throw new SymbolNotFoundException(code); }, LOG, "Stock");

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    void throwsTemporarilyUnavailable_onTransientUpstreamFailure() {
        // Arrange + Act + Assert: a transient failure must NOT collapse to "not found" — the caller needs to know
        // upstream is down so it can tell the user to retry instead of claiming the (valid) asset doesn't exist.
        assertThatThrownBy(() -> ApiAssetValidator.validate("aapl", true,
                code -> { throw new ExternalApiException("Yahoo", "boom", new RuntimeException("timeout")); },
                LOG, "Stock"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.market.dataTemporarilyUnavailable");
    }

    @Test
    void returnsFalse_whenCodeBlank() {
        // Arrange + Act: a blank code never reaches the lookup.
        boolean exists = ApiAssetValidator.validate("   ", true, code -> true, LOG, "Stock");

        // Assert
        assertThat(exists).isFalse();
    }
}
