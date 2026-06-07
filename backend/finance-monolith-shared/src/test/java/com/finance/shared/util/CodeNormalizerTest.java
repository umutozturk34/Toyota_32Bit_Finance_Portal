package com.finance.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CodeNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "btc,BTC",
            "  thyao  ,THYAO",
            "Gold,GOLD",
            "ALREADY,ALREADY"
    })
    void should_trimAndUppercase_when_codeIsNonNull(String raw, String expected) {
        // Arrange + Act
        String result = CodeNormalizer.upper(raw);

        // Assert
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "BTC,btc",
            "  THYAO  ,thyao",
            "Gold,gold",
            "already,already"
    })
    void should_trimAndLowercase_when_codeIsNonNull(String raw, String expected) {
        // Arrange + Act
        String result = CodeNormalizer.lower(raw);

        // Assert
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void should_returnEmptyString_when_upperGivenNull() {
        // Arrange + Act
        String result = CodeNormalizer.upper(null);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEmptyString_when_lowerGivenNull() {
        // Arrange + Act
        String result = CodeNormalizer.lower(null);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEmptyString_when_codeIsOnlyWhitespace() {
        // Arrange + Act
        String result = CodeNormalizer.upper("   ");

        // Assert
        assertThat(result).isEmpty();
    }
}
