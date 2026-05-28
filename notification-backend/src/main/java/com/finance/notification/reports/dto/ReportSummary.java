package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Portfolio headline figures for the report: totals, P/L (total/daily/realized) and CPI-adjusted growth. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportSummary(
        BigDecimal totalValueTry,
        BigDecimal totalEntryValueTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        BigDecimal dailyPnlTry,
        BigDecimal dailyPnlPercent,
        BigDecimal realPnlTry,
        BigDecimal realPnlPercent,
        BigDecimal cpiGrowthPercent
) {}
