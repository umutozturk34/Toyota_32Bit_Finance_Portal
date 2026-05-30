package com.finance.notification.reports.dto;

import java.time.LocalDateTime;

/** A single point of the portfolio value-over-time series (value in TRY). */
public record PerformanceSeriesPoint(LocalDateTime timestamp, double value) {}
