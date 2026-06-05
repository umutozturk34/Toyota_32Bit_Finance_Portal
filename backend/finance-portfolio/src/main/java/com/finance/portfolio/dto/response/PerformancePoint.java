package com.finance.portfolio.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * One point on a portfolio performance series: total value/cash/PnL in TRY at a timestamp, with per-asset
 * details and trade events. Per-currency maps (keyed USD/EUR) restate the point in non-TRY frames:
 * {@code valueByCcy} = displayed value (held at point FX; aggregate folds closed proceeds locked at exit FX),
 * {@code costBasisByCcy} = every lot's entry-date-FX cost, {@code realizedByCcy} = closed lots' realized PnL
 * locked at exit FX, {@code pnlByCcy} = total PnL (open + realized). The chart reads {@code pnlByCcy} directly
 * so it is correct whether or not the displayed value carries closed proceeds; TRY frame uses the TRY scalars.
 */
public record PerformancePoint(
        LocalDateTime timestamp,
        BigDecimal totalValueTry,
        BigDecimal cashTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        List<PerformanceAssetDetail> details,
        List<PerformanceEvent> events,
        Map<String, BigDecimal> costBasisByCcy,
        Map<String, BigDecimal> valueByCcy,
        Map<String, BigDecimal> realizedByCcy,
        Map<String, BigDecimal> pnlByCcy
) {
    /** Back-compat constructor for TRY-only callers/tests with no per-currency frame. */
    public PerformancePoint(LocalDateTime timestamp, BigDecimal totalValueTry, BigDecimal cashTry,
                            BigDecimal totalPnlTry, BigDecimal pnlPercent,
                            List<PerformanceAssetDetail> details, List<PerformanceEvent> events) {
        this(timestamp, totalValueTry, cashTry, totalPnlTry, pnlPercent, details, events,
                Map.of(), Map.of(), Map.of(), Map.of());
    }
    /**
     * Open-position unrealized P&L for the aggregate series: total lifecycle P&L minus the realized
     * (closed) portion. On the aggregate series {@code totalPnlTry} = unrealized + realized and
     * {@code cashTry} = cumulative realized P&L (both spot and VIOP folded in via SnapshotTotals), so
     * the difference equals open market value minus open cost. Surfaced for the Total/Open/Closed
     * P&L-over-time chart. Nulls count as zero.
     */
    @JsonProperty("openPnlTry")
    public BigDecimal openPnlTry() {
        BigDecimal total = totalPnlTry != null ? totalPnlTry : BigDecimal.ZERO;
        BigDecimal closed = cashTry != null ? cashTry : BigDecimal.ZERO;
        return total.subtract(closed);
    }
}
