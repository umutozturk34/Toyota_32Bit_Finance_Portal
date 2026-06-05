package com.finance.notification.reports.dto;

import java.util.List;

/**
 * All data needed to render one portfolio report. {@code allocation} is the regular Dağılım pie
 * (by asset-type with a single CASH bucket for closed lots); {@code realizedAllocation} is the
 * per-asset-type breakdown of realized P&L (drives the Winners/Losers section so it shows
 * "Hisse", "Kripto", "VİOP" instead of one collapsed "Nakit" row).
 */
public record PortfolioReportBundle(
        Long portfolioId,
        ReportSummary summary,
        List<ReportAllocation> allocation,
        List<ReportAllocation> realizedAllocation,
        List<ReportPosition> positions,
        List<PerformanceSeriesPoint> performanceSeries,
        List<PerformanceSeriesPoint> returnSeries
) {}
