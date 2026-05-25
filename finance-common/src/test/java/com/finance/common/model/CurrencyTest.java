package com.finance.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyTest {

    @Test
    void enumExposesTryUsdEur() {
        assertThat(Currency.values()).containsExactly(Currency.TRY, Currency.USD, Currency.EUR);
    }

    @ParameterizedTest
    @CsvSource({
            "TRY, TRY",
            "USD, USD",
            "EUR, EUR",
            "try, TRY",
            " usd , USD"
    })
    void fromCodeParsesKnownCurrencies(String input, Currency expected) {
        assertThat(Currency.fromCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GBP", "XAU", "  ", "TRYY"})
    void fromCodeReturnsNullForUnknown(String input) {
        assertThat(Currency.fromCode(input)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void fromCodeHandlesNullAndEmpty(String input) {
        assertThat(Currency.fromCode(input)).isNull();
    }
}
