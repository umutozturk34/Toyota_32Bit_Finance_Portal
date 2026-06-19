package com.finance.notification.reports.service;

import com.finance.notification.reports.dto.PerformanceSeriesPoint;
import com.finance.notification.reports.fx.ForexRatePoint;
import com.finance.notification.reports.fx.ReportFxConverter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCurrencyConverterTest {

    private final ReportCurrencyConverter converter = new ReportCurrencyConverter();

    /** A USD report whose only rate is 30.00 TRY/USD (forward-filled to every later date). */
    private static ReportFxConverter usdConverterAt30() {
        return new ReportFxConverter("USD", Map.of("USD",
                List.of(new ForexRatePoint(LocalDate.of(2026, 1, 1), new BigDecimal("30.00")))));
    }

    @Test
    void convertSeries_prefersLockedPerCurrencyValue_overRedividingTheTryScalar() {
        // A closed/frozen portfolio's USD value is locked (proceeds at exit FX); the report must plot THAT,
        // not re-divide the flat TRY scalar by the date's rate (which would wobble the tail in USD/EUR).
        LocalDateTime ts = LocalDateTime.of(2026, 6, 1, 0, 0);
        PerformanceSeriesPoint point = new PerformanceSeriesPoint(ts, 3000d, Map.of("USD", new BigDecimal("50")));

        List<PerformanceSeriesPoint> out = converter.convertSeries(List.of(point), usdConverterAt30());

        // Re-dividing would give 3000 / 30 = 100; the locked 50 USD must win.
        assertThat(out).hasSize(1);
        assertThat(out.get(0).value()).isEqualTo(50d);
    }

    @Test
    void convertSeries_fallsBackToPerDateFx_whenNoPerCurrencyValuePresent() {
        // Legacy point (no valueByCcy) → divide the TRY scalar by that date's FX, as before.
        LocalDateTime ts = LocalDateTime.of(2026, 6, 1, 0, 0);
        PerformanceSeriesPoint point = new PerformanceSeriesPoint(ts, 3000d);

        List<PerformanceSeriesPoint> out = converter.convertSeries(List.of(point), usdConverterAt30());

        assertThat(out).hasSize(1);
        assertThat(out.get(0).value()).isEqualTo(100d);
    }
}
