package com.finance.market.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class YahooRangePolicyTest {

    @ParameterizedTest
    @CsvSource({
            "0, 5d",
            "5, 5d",
            "6, 1mo",
            "30, 1mo",
            "31, 3mo",
            "90, 3mo",
            "91, 6mo",
            "180, 6mo",
            "181, 1y",
            "365, 1y",
            "366, 2y",
            "730, 2y",
            "731, 10y",
            "5000, 10y"
    })
    void fromGapDays_picksRangeMatchingGap(long gapDays, String expected) {
        String range = YahooRangePolicy.fromGapDays(gapDays, "10y");

        assertThat(range).isEqualTo(expected);
    }

    @Test
    void fromLastCandle_computesGapBasedOnZoneAwareToday() {
        ZoneId zone = ZoneOffset.UTC;
        LocalDateTime lastCandle = java.time.LocalDate.now(zone).minusDays(10).atStartOfDay();

        String range = YahooRangePolicy.fromLastCandle(lastCandle, zone, "10y");

        assertThat(range).isEqualTo("1mo");
    }
}
