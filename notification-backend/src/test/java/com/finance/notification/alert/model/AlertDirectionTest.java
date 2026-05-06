package com.finance.notification.alert.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AlertDirectionTest {

    @ParameterizedTest
    @CsvSource({
            "ABOVE, 100, 99, true",
            "ABOVE, 100, 100, true",
            "ABOVE, 99.99, 100, false",
            "BELOW, 100, 100, true",
            "BELOW, 100, 99, false",
            "BELOW, 99.99, 100, true"
    })
    void absoluteDirections_compareCurrentToThreshold(AlertDirection direction,
                                                     BigDecimal current,
                                                     BigDecimal threshold,
                                                     boolean expected) {
        boolean result = direction.isFired(current, null, threshold);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "CHANGE_PCT_UP, 105, 100, 5, true",
            "CHANGE_PCT_UP, 104.99, 100, 5, false",
            "CHANGE_PCT_UP, 110, 100, 5, true",
            "CHANGE_PCT_DOWN, 95, 100, 5, true",
            "CHANGE_PCT_DOWN, 95.01, 100, 5, false",
            "CHANGE_PCT_DOWN, 90, 100, 5, true"
    })
    void changePercentDirections_compareDeltaToThreshold(AlertDirection direction,
                                                        BigDecimal current,
                                                        BigDecimal reference,
                                                        BigDecimal thresholdPct,
                                                        boolean expected) {
        boolean result = direction.isFired(current, reference, thresholdPct);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"CHANGE_PCT_UP", "CHANGE_PCT_DOWN"})
    void changePercentDirections_returnFalseWhenReferenceMissingOrZero(AlertDirection direction) {
        boolean withNullRef = direction.isFired(BigDecimal.valueOf(110), null, BigDecimal.valueOf(5));
        boolean withZeroRef = direction.isFired(BigDecimal.valueOf(110), BigDecimal.ZERO, BigDecimal.valueOf(5));

        assertThat(withNullRef).isFalse();
        assertThat(withZeroRef).isFalse();
    }
}
