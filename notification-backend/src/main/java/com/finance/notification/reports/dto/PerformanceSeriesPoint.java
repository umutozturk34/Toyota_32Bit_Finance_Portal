package com.finance.notification.reports.dto;

import java.time.LocalDateTime;

public record PerformanceSeriesPoint(LocalDateTime timestamp, double value) {}
