package com.finance.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnMathTest {

    private static final int SCALE = 2;

    // returnPct, benchmarkPct, expectedExcessPct — geometric (1+r)/(1+b)-1, NOT naive r-b.
    @ParameterizedTest
    @CsvSource({
            "100,   25,      60.00",   // naive would be 75   — geometric purchasing-power excess is 60
            "10,    25,     -12.00",   // naive would be -15
            "50,    20,      25.00",   // real return: asset +50% vs CPI +20% -> +25% (Fisher)
            "6275,  403.5, 1166.14",   // PKZ-scale: naive +5871.5 pts vs real +1166% (x12.66, not x58.7)
            "47.85, 47.85,    0.00",   // equal -> zero excess either way
            "0,     0,        0.00",
            "-20,   10,     -27.27",    // loss vs a positive benchmark
    })
    void computesGeometricExcess(String ret, String bench, String expected) {
        // Arrange
        BigDecimal returnPct = new BigDecimal(ret);
        BigDecimal benchmarkPct = new BigDecimal(bench);

        // Act
        BigDecimal excess = ReturnMath.realExcessPct(returnPct, benchmarkPct, SCALE);

        // Assert
        assertThat(excess).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void signMatchesArithmeticSoBeatsVerdictIsUnchanged() {
        // Arrange + Act + Assert: just above the benchmark beats, just below loses — same as arithmetic sign.
        assertThat(ReturnMath.realExcessPct(new BigDecimal("50.01"), new BigDecimal("50"), SCALE).signum())
                .isEqualTo(1);
        assertThat(ReturnMath.realExcessPct(new BigDecimal("49.99"), new BigDecimal("50"), SCALE).signum())
                .isEqualTo(-1);
    }

    @Test
    void nullReturnYieldsNull() {
        // Arrange + Act + Assert
        assertThat(ReturnMath.realExcessPct(null, new BigDecimal("20"), SCALE)).isNull();
    }

    @Test
    void nullBenchmarkYieldsNull() {
        // Arrange + Act + Assert
        assertThat(ReturnMath.realExcessPct(new BigDecimal("50"), null, SCALE)).isNull();
    }

    @Test
    void benchmarkAtOrBelowMinus100YieldsNull() {
        // Arrange + Act + Assert: (1 + benchmark/100) <= 0 makes the ratio undefined.
        assertThat(ReturnMath.realExcessPct(new BigDecimal("50"), new BigDecimal("-100"), SCALE)).isNull();
        assertThat(ReturnMath.realExcessPct(new BigDecimal("50"), new BigDecimal("-150"), SCALE)).isNull();
    }

    @Test
    void respectsScale() {
        // Arrange + Act + Assert
        assertThat(ReturnMath.realExcessPct(new BigDecimal("6275"), new BigDecimal("403.5"), 4).scale())
                .isEqualTo(4);
    }
}
