package com.finance.common.model.value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTRYTest {

    @Test
    void constructorRoundsAmountToScaleFour() {
        MoneyTRY money = MoneyTRY.of(new BigDecimal("12.345678"));

        assertThat(money.amount()).isEqualByComparingTo("12.3457");
        assertThat(money.amount().scale()).isEqualTo(4);
    }

    @Test
    void constructorRejectsNullAmount() {
        assertThatThrownBy(() -> MoneyTRY.of((BigDecimal) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void zeroConstantHasZeroAmount() {
        assertThat(MoneyTRY.ZERO.amount()).isEqualByComparingTo("0");
        assertThat(MoneyTRY.ZERO.isZero()).isTrue();
    }

    @Test
    void plusAddsAmountsAndKeepsScale() {
        MoneyTRY result = MoneyTRY.of("10.50").plus(MoneyTRY.of("2.2525"));

        assertThat(result.amount()).isEqualByComparingTo("12.7525");
    }

    @Test
    void minusSubtractsAmounts() {
        MoneyTRY result = MoneyTRY.of("10").minus(MoneyTRY.of("3.25"));

        assertThat(result.amount()).isEqualByComparingTo("6.75");
    }

    @Test
    void multiplyScalesByFactor() {
        MoneyTRY result = MoneyTRY.of("100").multiply(new BigDecimal("0.05"));

        assertThat(result.amount()).isEqualByComparingTo("5.0000");
    }

    @Test
    void divideAppliesHalfUpRoundingAtScaleFour() {
        MoneyTRY result = MoneyTRY.of("10").divide(new BigDecimal("3"));

        assertThat(result.amount()).isEqualByComparingTo("3.3333");
    }

    @Test
    void negateInvertsSign() {
        assertThat(MoneyTRY.of("5").negate().amount()).isEqualByComparingTo("-5");
        assertThat(MoneyTRY.of("-5").negate().amount()).isEqualByComparingTo("5");
    }

    @ParameterizedTest
    @CsvSource({"0, true, false, false", "1, false, true, false", "-1, false, false, true"})
    void signHelpersReflectSign(String amount, boolean zero, boolean positive, boolean negative) {
        MoneyTRY money = MoneyTRY.of(amount);

        assertThat(money.isZero()).isEqualTo(zero);
        assertThat(money.isPositive()).isEqualTo(positive);
        assertThat(money.isNegative()).isEqualTo(negative);
    }

    @ParameterizedTest
    @CsvSource({"10, 5, true", "5, 5, true", "5, 10, false"})
    void isGreaterThanOrEqualToComparesAmounts(String left, String right, boolean expected) {
        assertThat(MoneyTRY.of(left).isGreaterThanOrEqualTo(MoneyTRY.of(right))).isEqualTo(expected);
    }

    @Test
    void compareToOrdersByAmount() {
        assertThat(MoneyTRY.of("1").compareTo(MoneyTRY.of("2"))).isNegative();
        assertThat(MoneyTRY.of("2").compareTo(MoneyTRY.of("1"))).isPositive();
        assertThat(MoneyTRY.of("1").compareTo(MoneyTRY.of("1"))).isZero();
    }

    @Test
    void ofLongCreatesMoneyWithIntegerAmount() {
        assertThat(MoneyTRY.of(1000L).amount()).isEqualByComparingTo("1000.0000");
    }
}
