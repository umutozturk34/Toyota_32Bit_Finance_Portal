package com.finance.common.util;

import com.finance.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchFailureGuardTest {

    @Test
    void belowThresholdDoesNotThrow() {
        assertThatCode(() -> BatchFailureGuard.check(8, 2, List.of("A", "B"), "STOCK"))
                .doesNotThrowAnyException();
    }

    @Test
    void exactlyAtThresholdDoesNotThrow() {
        assertThatCode(() -> BatchFailureGuard.check(5, 5, List.of("A", "B", "C", "D", "E"), "CRYPTO"))
                .doesNotThrowAnyException();
    }

    @Test
    void aboveThresholdWithSufficientSampleThrows() {
        assertThatThrownBy(() -> BatchFailureGuard.check(2, 4, List.of("A", "B", "C", "D"), "FOREX", 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Critical API failure")
                .hasMessageContaining("FOREX");
    }

    @Test
    void aboveThresholdButBelowMinSampleDoesNotThrow() {
        assertThatCode(() -> BatchFailureGuard.check(1, 3, List.of("A", "B", "C"), "FUND"))
                .doesNotThrowAnyException();
    }

    @Test
    void defaultMinSampleIsFive() {
        assertThatCode(() -> BatchFailureGuard.check(1, 3, List.of("A", "B", "C"), "STOCK"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> BatchFailureGuard.check(2, 4, List.of("A", "B", "C", "D"), "STOCK", 5))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void customMinSampleRespected() {
        assertThatThrownBy(() -> BatchFailureGuard.check(1, 2, List.of("A", "B"), "BOND", 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 out of 3");
    }

    @Test
    void allFailuresThrows() {
        assertThatThrownBy(() -> BatchFailureGuard.check(0, 6, List.of("A", "B", "C", "D", "E", "F"), "CRYPTO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("6 out of 6");
    }

    @Test
    void allSuccessDoesNotThrow() {
        assertThatCode(() -> BatchFailureGuard.check(10, 0, List.of(), "STOCK"))
                .doesNotThrowAnyException();
    }
}
