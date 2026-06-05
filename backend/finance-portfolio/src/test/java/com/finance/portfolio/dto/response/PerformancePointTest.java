package com.finance.portfolio.dto.response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PerformancePointTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 5, 1, 0, 0);

    private static PerformancePoint point(BigDecimal totalPnl, BigDecimal cash) {
        return new PerformancePoint(TS, BigDecimal.ZERO, cash, totalPnl,
                BigDecimal.ZERO, List.of(), List.of());
    }

    @ParameterizedTest
    @CsvSource({
            "100, 0, 100",      // all open: closed zero, open == total
            "100, 100, 0",      // all closed: open drained to zero, closed == total
            "150, 60, 90",      // mixed open + closed
            "-40, -25, -15",    // negative unrealized and negative realized
            "0, 0, 0",          // empty portfolio
            "50, -30, 80",      // realized loss while open positions in profit
    })
    void should_deriveOpenAsTotalMinusClosed_when_bothPresent(String total, String cash, String expectedOpen) {
        PerformancePoint p = point(new BigDecimal(total), new BigDecimal(cash));

        assertThat(p.openPnlTry()).isEqualByComparingTo(expectedOpen);
    }

    @Test
    void should_treatNullsAsZero_when_componentsMissing() {
        PerformancePoint p = new PerformancePoint(TS, null, null, null, null, List.of(), List.of());

        assertThat(p.openPnlTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_useTotalAsOpen_when_cashNull() {
        PerformancePoint p = new PerformancePoint(TS, BigDecimal.ZERO, null,
                new BigDecimal("75"), BigDecimal.ZERO, List.of(), List.of());

        assertThat(p.openPnlTry()).isEqualByComparingTo("75");
    }
}
