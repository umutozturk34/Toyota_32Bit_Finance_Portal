package com.finance.backend.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CandlePeriodTest {

    @ParameterizedTest
    @CsvSource({"ONE_MONTH, 1", "THREE_MONTHS, 3", "SIX_MONTHS, 6", "ONE_YEAR, 12", "FIVE_YEARS, 60", "ALL, 0"})
    void monthsReturnCorrectValue(CandlePeriod period, int expectedMonths) {
        assertThat(period.getMonths()).isEqualTo(expectedMonths);
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
