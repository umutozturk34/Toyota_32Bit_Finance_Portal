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

    @ParameterizedTest
    @CsvSource({
            "F_USDTRY0626, TRY",
            "F_EURTRY0626, TRY",
            "F_EURUSD0626, USD",
            "F_GBPUSD0626, USD",
            "F_XAUUSD0626, USD",
            "F_XAGUSD0626, USD",
            "F_XAUTRYM0626, TRY",
            "F_XU0300626, TRY",
            "F_AKBNK0626, TRY",
            "f_eurusd0626, USD"
    })
    void viopFutureQuoteCurrencyDerivesCurrencyTokenBeforeExpiry(String symbol, Currency expected) {
        assertThat(Currency.viopQuoteCurrencyOf(symbol)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "O_AKBNKE0626C72.00",
            "O_XU030E0826P17250.00",
            "O_USDTRYKE0626C47000",
            "O_THYAOE0626P310.00"
    })
    void viopOptionsAlwaysQuoteInTry(String symbol) {
        assertThat(Currency.viopQuoteCurrencyOf(symbol)).isEqualTo(Currency.TRY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void viopQuoteCurrencyDefaultsToTryForBlank(String symbol) {
        assertThat(Currency.viopQuoteCurrencyOf(symbol)).isEqualTo(Currency.TRY);
    }
}
