package com.finance.shared.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CandlePeriodTest {

    private static final LocalDateTime REFERENCE_END = LocalDateTime.of(2026, 4, 19, 12, 0);

    @ParameterizedTest
    @CsvSource({"ONE_MONTH, 1", "THREE_MONTHS, 3", "SIX_MONTHS, 6", "ONE_YEAR, 12", "FIVE_YEARS, 60", "ALL, 0"})
    void monthsReturnCorrectValue(CandlePeriod period, int expectedMonths) {
        assertThat(period.getMonths()).isEqualTo(expectedMonths);
    }

    @ParameterizedTest
    @CsvSource({
            "1M,  ONE_MONTH",
            "3M,  THREE_MONTHS",
            "6M,  SIX_MONTHS",
            "1Y,  ONE_YEAR",
            "5Y,  FIVE_YEARS",
            "ALL, ALL",
            "1m,  ONE_MONTH",
            "all, ALL"
    })
    void fromCodeMatchesKnownCodesCaseInsensitively(String code, CandlePeriod expected) {
        assertThat(CandlePeriod.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "2Y", "1month"})
    @EmptySource
    void fromCodeFallsBackToOneMonthForUnknown(String code) {
        assertThat(CandlePeriod.fromCode(code)).isEqualTo(CandlePeriod.ONE_MONTH);
    }

    @Test
    void fromCodeReturnsOneMonthForNull() {
        assertThat(CandlePeriod.fromCode(null)).isEqualTo(CandlePeriod.ONE_MONTH);
    }

    @ParameterizedTest
    @CsvSource({
            "ONE_MONTH,    2026-03-19T12:00",
            "THREE_MONTHS, 2026-01-19T12:00",
            "SIX_MONTHS,   2025-10-19T12:00",
            "ONE_YEAR,     2025-04-19T12:00",
            "FIVE_YEARS,   2021-04-19T12:00"
    })
    void toStartDateTimeWithEndShiftsBackByPeriod(CandlePeriod period, LocalDateTime expected) {
        assertThat(period.toStartDateTime(REFERENCE_END)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1M, ONE_MONTH", "5Y, FIVE_YEARS", "ALL, ALL"})
    void getCodeReturnsLiteralOfEachConstant(String expectedCode, CandlePeriod period) {
        assertThat(period.getCode()).isEqualTo(expectedCode);
    }

    @Test
    void toStartDateTimeAllReturnsEpoch() {
        LocalDateTime start = CandlePeriod.ALL.toStartDateTime();

        assertThat(start).isEqualTo(LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    @Test
    void toStartDateTimeOneMonthReturnsStartOfDayOneMonthAgo() {
        LocalDateTime expected = LocalDateTime.now().minusMonths(1).toLocalDate().atStartOfDay();

        LocalDateTime start = CandlePeriod.ONE_MONTH.toStartDateTime();

        assertThat(start).isEqualTo(expected);
    }

    @Test
    void toStartDateTimeFiveYearsReturnsStartOfDayFiveYearsAgo() {
        LocalDateTime expected = LocalDateTime.now().minusMonths(60).toLocalDate().atStartOfDay();

        LocalDateTime start = CandlePeriod.FIVE_YEARS.toStartDateTime();

        assertThat(start).isEqualTo(expected);
    }

    @Test
    void toStartDateAllReturnsEpoch() {
        LocalDate start = CandlePeriod.ALL.toStartDate();

        assertThat(start).isEqualTo(LocalDate.of(1970, 1, 1));
    }

    @Test
    void toStartDateSixMonthsReturnsSixMonthsAgo() {
        LocalDate start = CandlePeriod.SIX_MONTHS.toStartDate();

        assertThat(start).isEqualTo(LocalDate.now().minusMonths(6));
    }
}
