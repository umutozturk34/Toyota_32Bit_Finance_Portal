package com.finance.notification.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * A single point of the portfolio value-over-time series (value in TRY). {@code valueByCcy} optionally carries
 * the backend's per-currency value frame (closed-lot proceeds locked at exit FX) so a non-TRY report can plot
 * the value directly instead of re-dividing the flat TRY scalar by a moving daily rate — which would make a
 * closed/frozen portfolio's tail wobble in USD/EUR. Null for the return series and for legacy 2-arg construction.
 */
public record PerformanceSeriesPoint(LocalDateTime timestamp, double value, Map<String, BigDecimal> valueByCcy) {
    public PerformanceSeriesPoint(LocalDateTime timestamp, double value) {
        this(timestamp, value, null);
    }
}
