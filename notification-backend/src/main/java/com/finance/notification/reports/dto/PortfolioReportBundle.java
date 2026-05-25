package com.finance.notification.reports.dto;

import java.util.List;

public record PortfolioReportBundle(
        Long portfolioId,
        ReportSummary summary,
        List<ReportAllocation> allocation,
        List<ReportPosition> positions,
        List<PerformanceSeriesPoint> performanceSeries
) {}
