package com.finance.notification.reports.dto;

import java.util.List;

/** All data needed to render one portfolio report: summary, allocation, positions and the value series. */
public record PortfolioReportBundle(
        Long portfolioId,
        ReportSummary summary,
        List<ReportAllocation> allocation,
        List<ReportPosition> positions,
        List<PerformanceSeriesPoint> performanceSeries
) {}
