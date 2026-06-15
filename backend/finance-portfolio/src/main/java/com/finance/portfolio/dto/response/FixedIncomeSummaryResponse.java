package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Headline totals for the standalone "Mevduat &amp; Tahvil" (deposit + Türkiye Hazine bond) view, all in TRY.
 * {@code totalCostTry} is the FX-at-entry deposit principal plus bond entry value; {@code totalValueTry} the
 * live accrued/realized deposit value plus bond clean-price valuation; {@code totalPnlTry} their difference.
 * {@code pnlPercent} is null when cost is zero (no positions, or fully-zero cost) to avoid a divide-by-zero.
 * The per-kind split ({@code depositValueTry}/{@code bondValueTry}, {@code depositCount}/{@code bondCount})
 * drives the allocation breakdown; {@code asOf} is the valuation date.
 *
 * <p>{@code bondCouponsReceivedTry} is the cumulative TRY coupon cash already received across all bonds (each past
 * coupon priced at the .ORAN in effect on its own date). It is realized cash on top of the mark-to-market value,
 * so the client adds it to {@code totalValueTry} for the holder's true total ("value + coupons") and folds it into
 * the displayed K/Z — {@code totalValueTry}/{@code totalPnlTry} themselves stay pure clean-price mark-to-market so
 * they keep reconciling with the per-row grid and the history series' value leg.
 */
public record FixedIncomeSummaryResponse(
        BigDecimal totalCostTry,
        BigDecimal totalValueTry,
        BigDecimal totalPnlTry,
        BigDecimal pnlPercent,
        int depositCount,
        int bondCount,
        BigDecimal depositValueTry,
        BigDecimal bondValueTry,
        BigDecimal bondCouponsReceivedTry,
        LocalDate asOf
) {
}
