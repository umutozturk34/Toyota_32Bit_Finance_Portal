package com.finance.shared.model.value;

import com.finance.common.model.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void ofConstructsAndRoundsToScaleFour() {
        Money money = Money.of(new BigDecimal("12.345678"), Currency.USD);

        assertThat(money.amount()).isEqualByComparingTo("12.3457");
        assertThat(money.amount().scale()).isEqualTo(4);
        assertThat(money.currency()).isEqualTo(Currency.USD);
    }

    @Test
    void constructorRejectsNullAmount() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null, Currency.TRY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void constructorRejectsNullCurrency() {
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void plusAddsAmountsWhenCurrenciesMatch() {
        Money sum = Money.of("10.50", Currency.TRY).plus(Money.of("2.25", Currency.TRY));

        assertThat(sum.amount()).isEqualByComparingTo("12.75");
        assertThat(sum.currency()).isEqualTo(Currency.TRY);
    }

    @Test
    void plusRejectsDifferentCurrencies() {
        Money lhs = Money.of("10", Currency.TRY);
        Money rhs = Money.of("10", Currency.USD);

        assertThatThrownBy(() -> lhs.plus(rhs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRY")
                .hasMessageContaining("USD");
    }

    @Test
    void minusSubtractsAmountsWhenCurrenciesMatch() {
        Money diff = Money.of("10", Currency.EUR).minus(Money.of("3.25", Currency.EUR));

        assertThat(diff.amount()).isEqualByComparingTo("6.75");
    }

    @Test
    void minusRejectsDifferentCurrencies() {
        Money lhs = Money.of("10", Currency.TRY);
        Money rhs = Money.of("3", Currency.EUR);

        assertThatThrownBy(() -> lhs.minus(rhs))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiplyScalesByFactor() {
        Money result = Money.of("100", Currency.USD).multiply(new BigDecimal("0.05"));

        assertThat(result.amount()).isEqualByComparingTo("5.0000");
        assertThat(result.currency()).isEqualTo(Currency.USD);
    }

    @Test
    void inCurrencySameTargetReturnsSameAmount() {
        Money money = Money.of("100", Currency.TRY);

        Money same = money.inCurrency(Currency.TRY, new BigDecimal("2"));

        assertThat(same).isEqualTo(money);
    }

    @Test
    void inCurrencyAppliesRateAndSwitchesCurrency() {
        Money usd = Money.of("100", Currency.USD);

        Money tryMoney = usd.inCurrency(Currency.TRY, new BigDecimal("30"));

        assertThat(tryMoney.amount()).isEqualByComparingTo("3000.0000");
        assertThat(tryMoney.currency()).isEqualTo(Currency.TRY);
    }

    @Test
    void inCurrencyRejectsNonPositiveRate() {
        Money money = Money.of("100", Currency.USD);

        assertThatThrownBy(() -> money.inCurrency(Currency.TRY, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isZeroReflectsAmountSign() {
        assertThat(Money.zero(Currency.TRY).isZero()).isTrue();
        assertThat(Money.of("1", Currency.TRY).isZero()).isFalse();
    }

    @Test
    void isPositiveAndNegativeReflectSign() {
        assertThat(Money.of("1", Currency.TRY).isPositive()).isTrue();
        assertThat(Money.of("-1", Currency.TRY).isNegative()).isTrue();
    }
}
