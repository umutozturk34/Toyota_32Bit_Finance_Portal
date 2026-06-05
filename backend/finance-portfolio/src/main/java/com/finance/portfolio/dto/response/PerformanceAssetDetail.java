package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One asset/type slice within a performance point: its label, market value and PnL in TRY, plus
 * {@code costBasisByCcy}/{@code valueByCcy}/{@code pnlByCcy} (keyed USD/EUR) so a non-TRY frame shows this
 * slice's PnL as the per-date DIRECTION-AWARE frame (value − entry-date-FX cost, with the open-VIOP SHORT sign
 * correction baked into value) rather than a TRY scalar re-valued at one rate. {@code pnlByCcy} is what the
 * K/Z-contribution breakdown reads — without it the frontend was forced to convert {@code pnlTry} at today's FX,
 * which mis-priced an open VIOP slice (the direction-blind notional × today's rate).
 */
public record PerformanceAssetDetail(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal pnlTry,
        Map<String, BigDecimal> costBasisByCcy,
        Map<String, BigDecimal> valueByCcy,
        Map<String, BigDecimal> pnlByCcy
) {
    /** Back-compat constructor for TRY-only callers/aggregation. */
    public PerformanceAssetDetail(String label, String assetType, BigDecimal valueTry, BigDecimal pnlTry) {
        this(label, assetType, valueTry, pnlTry, Map.of(), Map.of(), Map.of());
    }

    /** Returns a copy of this slice with per-currency frame maps (cost, value, direction-aware PnL) attached. */
    public PerformanceAssetDetail withFrames(Map<String, BigDecimal> cost, Map<String, BigDecimal> value,
                                             Map<String, BigDecimal> pnl) {
        return new PerformanceAssetDetail(label, assetType, valueTry, pnlTry, cost, value, pnl);
    }
}
