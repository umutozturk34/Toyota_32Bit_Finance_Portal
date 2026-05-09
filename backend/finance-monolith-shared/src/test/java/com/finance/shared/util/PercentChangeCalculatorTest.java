package com.finance.shared.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PercentChangeCalculatorTest {

    private static final int SCALE = 4;

    @Test
    void computePositiveChange() {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(
                new BigDecimal("110"), new BigDecimal("100"), SCALE);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void computeNegativeChange() {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(
                new BigDecimal("90"), new BigDecimal("100"), SCALE);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("-10.0000"));
        assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("-10.0000"));
    }

    @Test
    void computeZeroChange() {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(
                new BigDecimal("100"), new BigDecimal("100"), SCALE);

        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.percent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeNullCurrentReturnsEmpty() {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(
                null, new BigDecimal("100"), SCALE);

        assertThat(result).isEqualTo(PercentChangeCalculator.Result.EMPTY);
    }

    @Test
    void computeNullPreviousReturnsEmpty() {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(
                new BigDecimal("100"), null, SCALE);

        assertThat(result).isEqualTo(PercentChangeCalculator.Result.EMPTY);
    }

    @Test
    void computeZeroPreviousReturnsEmpty() {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(
                new BigDecimal("100"), BigDecimal.ZERO, SCALE);

        assertThat(result).isEqualTo(PercentChangeCalculator.Result.EMPTY);
    }

    @Test
    void computeRespectsScale() {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(
                new BigDecimal("103.3333"), new BigDecimal("100"), 2);

        assertThat(result.amount().scale()).isEqualTo(2);
        assertThat(result.percent().scale()).isEqualTo(2);
    }
}
