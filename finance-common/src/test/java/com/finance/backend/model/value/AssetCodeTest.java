package com.finance.backend.model.value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetCodeTest {

    @ParameterizedTest
    @CsvSource({"btc, BTC", "Eth, ETH", "AAPL, AAPL", "  GARAN  , GARAN"})
    void normalizesInputToUppercaseAndTrimmed(String input, String expected) {
        AssetCode code = AssetCode.of(input);

        assertThat(code.value()).isEqualTo(expected);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> AssetCode.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void rejectsBlankInput(String blank) {
        assertThatThrownBy(() -> AssetCode.of(blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void equalsByValueAfterNormalization() {
        assertThat(AssetCode.of("btc")).isEqualTo(AssetCode.of("BTC"));
        assertThat(AssetCode.of("  eth  ")).isEqualTo(AssetCode.of("ETH"));
    }

    @Test
    void toStringReturnsNormalizedValue() {
        assertThat(AssetCode.of("xu100").toString()).isEqualTo("XU100");
    }
}
