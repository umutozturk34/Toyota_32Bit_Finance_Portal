package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Map;

/** Per-asset-type allocation slice with value, share, cost and realized P/L (all in TRY). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportAllocation(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal percent,
        BigDecimal costTry,
        BigDecimal realizedPnlTry,
        /**
         * Realized P/L converted at each closed lot's own close-date FX, keyed by currency code
         * (USD/EUR). Mirror of the backend's {@code AllocationItem.realizedPnlByCurrency} and the
         * on-screen RealizedPnlChart, so the report uses the per-close-date figure instead of
         * re-converting the TRY scalar at today's rate.
         */
        Map<String, BigDecimal> realizedPnlByCurrency,
        /**
         * Cost basis converted at each closed lot's own entry-date FX, keyed by currency code
         * (USD/EUR). Mirror of the backend's {@code AllocationItem.costByCurrency}, so realized gain
         * and its cost basis are rendered on matching per-date FX instead of mixing per-date realized
         * with today's-spot cost.
         */
        Map<String, BigDecimal> costByCurrency
) {
    /** Convenience constructor for callers/tests that only carry the TRY scalars (no per-currency frames). */
    public ReportAllocation(String label, String assetType, BigDecimal valueTry, BigDecimal percent,
                            BigDecimal costTry, BigDecimal realizedPnlTry) {
        this(label, assetType, valueTry, percent, costTry, realizedPnlTry, Map.of(), Map.of());
    }
}
