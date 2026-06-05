package com.finance.notification.reports.fx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link ReportFxConverter} mirrors the frontend {@code useRateHistory} rate resolution:
 * TRY pass-through, exact-date conversion, weekend/holiday forward-fill, pre-history earliest-rate
 * fallback, and graceful fallback on a missing/unusable rate.
 */
class ReportFxConverterTest {

    // USD rises 30→32 over a Thu/Fri; EUR is a single 35.0 point. Sat/Sun have no point (forward-fill).
    private static Map<String, List<ForexRatePoint>> series() {
        return Map.of(
                "USD", List.of(
                        new ForexRatePoint(LocalDate.of(2026, 5, 7), new BigDecimal("30.00")),  // Thursday
                        new ForexRatePoint(LocalDate.of(2026, 5, 8), new BigDecimal("32.00"))),  // Friday
                "EUR", List.of(
                        new ForexRatePoint(LocalDate.of(2026, 5, 7), new BigDecimal("35.00"))));
    }

    @Test
    void should_passThrough_when_targetIsTry() {
        // Arrange
        ReportFxConverter converter = new ReportFxConverter("TRY", series());

        // Act
        BigDecimal result = converter.convertFromTry(new BigDecimal("1500"), LocalDate.of(2026, 5, 8));

        // Assert
        assertThat(result).isEqualByComparingTo("1500");
    }

    @Test
    void should_returnOneRate_when_currencyIsTry() {
        // Arrange
        ReportFxConverter converter = new ReportFxConverter("USD", series());

        // Act
        BigDecimal rate = converter.rateAt("TRY", LocalDate.of(2026, 5, 8));

        // Assert
        assertThat(rate).isEqualByComparingTo("1");
    }

    @Test
    void should_convertUsdAtExactDate_when_dateHasOwnRate() {
        // Arrange — 3200 TRY at the Friday 32.00 rate => 100 USD
        ReportFxConverter converter = new ReportFxConverter("USD", series());

        // Act
        BigDecimal result = converter.convertFromTry(new BigDecimal("3200"), LocalDate.of(2026, 5, 8));

        // Assert
        assertThat(result).isEqualByComparingTo("100");
    }

    @Test
    void should_convertEur_when_targetIsEur() {
        // Arrange — 3500 TRY at the 35.00 rate => 100 EUR
        ReportFxConverter converter = new ReportFxConverter("EUR", series());

        // Act
        BigDecimal result = converter.convertFromTry(new BigDecimal("3500"), LocalDate.of(2026, 5, 7));

        // Assert
        assertThat(result).isEqualByComparingTo("100");
    }

    /**
     * Saturday (5-09) and Sunday (5-10) have no point, so the most recent prior trading day (Friday's
     * 32.00) must be reused; a date past all history also forward-fills to the latest point.
     */
    @ParameterizedTest
    @CsvSource({
            "2026-05-09, 100",  // Saturday → Friday rate
            "2026-05-10, 100",  // Sunday → Friday rate
            "2026-06-01, 100"   // weeks later → Friday rate (latest known)
    })
    void should_forwardFill_when_dateIsWeekendOrAfterHistory(String date, String expectedUsd) {
        // Arrange
        ReportFxConverter converter = new ReportFxConverter("USD", series());

        // Act
        BigDecimal result = converter.convertFromTry(new BigDecimal("3200"), LocalDate.parse(date));

        // Assert
        assertThat(result).isEqualByComparingTo(expectedUsd);
    }

    @Test
    void should_useEarliestRate_when_datePrecedesAllHistory() {
        // Arrange — date before the earliest point (5-07) must use that earliest 30.00 rate, not spot
        ReportFxConverter converter = new ReportFxConverter("USD", series());

        // Act — 3000 TRY at 30.00 => 100 USD
        BigDecimal result = converter.convertFromTry(new BigDecimal("3000"), LocalDate.of(2026, 1, 1));

        // Assert
        assertThat(result).isEqualByComparingTo("100");
    }

    @Test
    void should_returnNullRate_when_seriesMissing() {
        // Arrange — no USD history loaded
        ReportFxConverter converter = new ReportFxConverter("USD", Map.of());

        // Act
        BigDecimal rate = converter.rateAt("USD", LocalDate.of(2026, 5, 8));

        // Assert
        assertThat(rate).isNull();
    }

    @Test
    void should_fallBackToTryValue_when_rateUnavailable() {
        // Arrange — no USD history, so conversion can't happen and the lira value passes through
        ReportFxConverter converter = new ReportFxConverter("USD", Map.of());

        // Act
        BigDecimal result = converter.convertFromTry(new BigDecimal("3200"), LocalDate.of(2026, 5, 8));

        // Assert
        assertThat(result).isEqualByComparingTo("3200");
    }

    @Test
    void should_useEightScaleHalfUp_when_dividing() {
        // Arrange — 100 / 30.00 = 3.33333333 at scale 8 HALF_UP
        ReportFxConverter converter = new ReportFxConverter("USD", series());

        // Act
        BigDecimal result = converter.convertFromTry(new BigDecimal("100"), LocalDate.of(2026, 5, 7));

        // Assert
        assertThat(result).isEqualTo(new BigDecimal("3.33333333"));
    }

    @Test
    void should_passThroughNull_when_valueIsNull() {
        // Arrange
        ReportFxConverter converter = new ReportFxConverter("USD", series());

        // Act
        BigDecimal result = converter.convertFromTry(null, LocalDate.of(2026, 5, 8));

        // Assert
        assertThat(result).isNull();
    }
}
