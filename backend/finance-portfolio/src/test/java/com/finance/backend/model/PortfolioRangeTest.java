package com.finance.backend.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioRangeTest {

    private static final LocalDateTime REFERENCE_END = LocalDateTime.of(2026, 4, 19, 12, 0);

    @ParameterizedTest
    @CsvSource({
            "1M,  ONE_MONTH",
            "3M,  THREE_MONTHS",
            "6M,  SIX_MONTHS",
            "1Y,  ONE_YEAR",
            "ALL, ALL",
            "1m,  ONE_MONTH",
            "all, ALL"
    })
    void fromCodeMatchesKnownCodesCaseInsensitively(String code, PortfolioRange expected) {
        PortfolioRange actual = PortfolioRange.fromCode(code);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fromCodeFallsBackToOneMonthForNullInput() {
        PortfolioRange actual = PortfolioRange.fromCode(null);

        assertThat(actual).isEqualTo(PortfolioRange.ONE_MONTH);
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {"    ", "unknown", "2M"})
    void fromCodeFallsBackToOneMonthForUnknownInput(String code) {
        PortfolioRange actual = PortfolioRange.fromCode(code);

        assertThat(actual).isEqualTo(PortfolioRange.ONE_MONTH);
    }

    @Test
    void toStartDateTimeForOneMonthSubtractsOneMonth() {
        assertThat(PortfolioRange.ONE_MONTH.toStartDateTime(REFERENCE_END))
                .isEqualTo(REFERENCE_END.minusMonths(1));
    }

    @Test
    void toStartDateTimeForThreeMonthsSubtractsThreeMonths() {
        assertThat(PortfolioRange.THREE_MONTHS.toStartDateTime(REFERENCE_END))
                .isEqualTo(REFERENCE_END.minusMonths(3));
    }

    @Test
    void toStartDateTimeForSixMonthsSubtractsSixMonths() {
        assertThat(PortfolioRange.SIX_MONTHS.toStartDateTime(REFERENCE_END))
                .isEqualTo(REFERENCE_END.minusMonths(6));
    }

    @Test
    void toStartDateTimeForOneYearSubtractsOneYear() {
        assertThat(PortfolioRange.ONE_YEAR.toStartDateTime(REFERENCE_END))
                .isEqualTo(REFERENCE_END.minusYears(1));
    }

    @Test
    void toStartDateTimeForAllSubtractsTenYears() {
        assertThat(PortfolioRange.ALL.toStartDateTime(REFERENCE_END))
                .isEqualTo(REFERENCE_END.minusYears(10));
    }

    @ParameterizedTest
    @CsvSource({
            "ONE_MONTH,    1M",
            "THREE_MONTHS, 3M",
            "SIX_MONTHS,   6M",
            "ONE_YEAR,     1Y",
            "ALL,          ALL"
    })
    void codeExposesLiteralRepresentationOfEachConstant(PortfolioRange range, String expectedCode) {
        assertThat(range.code()).isEqualTo(expectedCode);
    }
}
