package com.finance.portfolio.fixedincome.bond;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CouponFrequency#fromStepMonths}: maps a coupon period in months (as inferred from a
 * bond's price-drop spacing) back onto the enum, and rejects non-standard/non-positive periods. AAA.
 */
class CouponFrequencyTest {

    @ParameterizedTest
    @CsvSource({
            "1,  MONTHLY",
            "3,  QUARTERLY",
            "6,  SEMI_ANNUAL",
            "12, ANNUAL",
    })
    void shouldMapStandardPeriodToFrequency(int months, CouponFrequency expected) {
        // Act + Assert
        assertThat(CouponFrequency.fromStepMonths(months)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3, 5, 7, 24})
    void shouldReturnNull_forNonStandardOrNonPositivePeriod(int months) {
        // Act + Assert: only 1/3/6/12 are standard coupon periods.
        assertThat(CouponFrequency.fromStepMonths(months)).isNull();
    }
}
