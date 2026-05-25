package com.finance.market.forex.model;

import com.finance.common.model.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FxRateTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 25);
    private static final BigDecimal RATE = new BigDecimal("32.50");

    @Test
    void constructor_buildsRecord_whenAllFieldsValid() {
        FxRate rate = new FxRate(DATE, Currency.USD, Currency.TRY, RATE);

        assertThat(rate.date()).isEqualTo(DATE);
        assertThat(rate.from()).isEqualTo(Currency.USD);
        assertThat(rate.to()).isEqualTo(Currency.TRY);
        assertThat(rate.rate()).isEqualByComparingTo(RATE);
    }

    @Test
    void constructor_throws_whenDateNull() {
        assertThatThrownBy(() -> new FxRate(null, Currency.USD, Currency.TRY, RATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void constructor_throws_whenFromCurrencyNull() {
        assertThatThrownBy(() -> new FxRate(DATE, null, Currency.TRY, RATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from currency");
    }

    @Test
    void constructor_throws_whenToCurrencyNull() {
        assertThatThrownBy(() -> new FxRate(DATE, Currency.USD, null, RATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to currency");
    }

    @Test
    void constructor_throws_whenRateNull() {
        assertThatThrownBy(() -> new FxRate(DATE, Currency.USD, Currency.TRY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate must be positive");
    }

    @Test
    void constructor_throws_whenRateZero() {
        assertThatThrownBy(() -> new FxRate(DATE, Currency.USD, Currency.TRY, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate must be positive");
    }

    @Test
    void constructor_throws_whenRateNegative() {
        assertThatThrownBy(() -> new FxRate(DATE, Currency.USD, Currency.TRY, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate must be positive");
    }
}
