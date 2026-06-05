package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Map;

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
        BigDecimal cpiGrowthPercent,
        /**
         * Per-currency frames keyed by currency code (USD/EUR). Each frame's {@code totalEntry} is the
         * portfolio's Total Cost already converted at each lot's own entry-date FX (mirror of the
         * backend's {@code CurrencyFramePct} and the on-screen SummaryCards), so the report can use it
         * directly instead of re-converting the TRY scalar at today's rate.
         */
        Map<String, ReportCurrencyFrame> frames
) {
    /** Convenience constructor for callers/tests that only carry the TRY scalars (no per-currency frames). */
    public ReportSummary(BigDecimal totalValueTry, BigDecimal totalEntryValueTry, BigDecimal totalPnlTry,
                         BigDecimal pnlPercent, BigDecimal dailyPnlTry, BigDecimal dailyPnlPercent,
                         BigDecimal realPnlTry, BigDecimal realPnlPercent, BigDecimal cpiGrowthPercent) {
        this(totalValueTry, totalEntryValueTry, totalPnlTry, pnlPercent, dailyPnlTry, dailyPnlPercent,
                realPnlTry, realPnlPercent, cpiGrowthPercent, Map.of());
    }

    /**
     * One currency frame, mirroring the backend {@code CurrencyFramePct}: value, cost and P&L are each
     * already converted at the right per-date FX. The report must consume {@code totalValue}/{@code totalPnl}/
     * {@code pnlPercent} from here (like the on-screen SummaryCards) so the document reconciles
     * (Value − Cost = P&L) and the amount carries its own-currency return %, not the TRY %.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReportCurrencyFrame(
            BigDecimal pnlPercent,
            BigDecimal dailyPnlPercent,
            BigDecimal totalValue,
            BigDecimal totalEntry,
            BigDecimal totalPnl,
            BigDecimal dailyPnl) {}
}
