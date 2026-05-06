package com.finance.common.model.value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PercentageTest {

    @Test
    void constructorRoundsToScaleFour() {
        Percentage pct = Percentage.of(new BigDecimal("12.345678"));

        assertThat(pct.value()).isEqualByComparingTo("12.3457");
        assertThat(pct.value().scale()).isEqualTo(4);
    }

    @Test
    void constructorRejectsNullValue() {
        assertThatThrownBy(() -> Percentage.of((BigDecimal) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void zeroConstantReturnsZero() {
        assertThat(Percentage.ZERO.value()).isEqualByComparingTo("0");
        assertThat(Percentage.ZERO.isZero()).isTrue();
    }

    @Test
    void ofRatioComputesPercentage() {
        Percentage pct = Percentage.ofRatio(new BigDecimal("25"), new BigDecimal("100"));

        assertThat(pct.value()).isEqualByComparingTo("25.0000");
    }

    @Test
    void ofRatioReturnsZeroWhenDenominatorIsZero() {
        Percentage pct = Percentage.ofRatio(new BigDecimal("10"), BigDecimal.ZERO);

        assertThat(pct).isEqualTo(Percentage.ZERO);
    }

    @Test
    void ofRatioReturnsZeroWhenDenominatorIsNull() {
        Percentage pct = Percentage.ofRatio(new BigDecimal("10"), null);

        assertThat(pct).isEqualTo(Percentage.ZERO);
    }

    @Test
    void asFractionDividesByOneHundred() {
        BigDecimal fraction = Percentage.of("5").asFraction();

        assertThat(fraction).isEqualByComparingTo("0.05");
    }

    @Test
    void applyToMultipliesMoneyByFraction() {
        MoneyTRY base = MoneyTRY.of("1000");
        Percentage tax = Percentage.of("20");

        MoneyTRY taxAmount = tax.applyTo(base);

        assertThat(taxAmount.amount()).isEqualByComparingTo("200.0000");
    }

    @ParameterizedTest
    @CsvSource({"0, true, false, false", "1.5, false, true, false", "-1.5, false, false, true"})
    void signHelpersReflectSign(String value, boolean zero, boolean positive, boolean negative) {
        Percentage pct = Percentage.of(value);

        assertThat(pct.isZero()).isEqualTo(zero);
        assertThat(pct.isPositive()).isEqualTo(positive);
        assertThat(pct.isNegative()).isEqualTo(negative);
    }

    @Test
    void compareToOrdersByValue() {
        assertThat(Percentage.of("1").compareTo(Percentage.of("2"))).isNegative();
        assertThat(Percentage.of("2").compareTo(Percentage.of("1"))).isPositive();
        assertThat(Percentage.of("1").compareTo(Percentage.of("1"))).isZero();
    }
}
