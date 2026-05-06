package com.finance.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class YahooRangePolicyTest {

    @ParameterizedTest
    @CsvSource({
            "0, max, 5d",
            "5, max, 5d",
            "6, max, 1mo",
            "30, max, 1mo",
            "31, max, 3mo",
            "90, max, 3mo",
            "91, max, 6mo",
            "180, max, 6mo",
            "181, max, 1y",
            "365, max, 1y",
            "366, max, 2y",
            "730, max, 2y",
            "731, max, max"
    })
    void mapsGapDaysToCorrectRange(long gapDays, String maxRange, String expectedRange) {
        assertThat(YahooRangePolicy.fromGapDays(gapDays, maxRange)).isEqualTo(expectedRange);
    }

    @Test
    void gapBeyond730DaysUsesProvidedMaxRange() {
        assertThat(YahooRangePolicy.fromGapDays(731, "10y")).isEqualTo("10y");
        assertThat(YahooRangePolicy.fromGapDays(1000, "5y")).isEqualTo("5y");
    }
}
