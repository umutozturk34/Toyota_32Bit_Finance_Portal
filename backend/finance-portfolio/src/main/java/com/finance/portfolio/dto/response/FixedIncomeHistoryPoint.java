package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day on the fixed-income value-over-time series, all in TRY: the deposit leg, the bond leg, their sum,
 * and the cumulative cost basis of every holding online on this date. A holding contributes zero before its
 * start/entry date, so both the legs and the cost grow as positions come online. {@code totalCostTry} lets the
 * client draw a profit/loss (K/Z = value − cost) curve and, paired with a CPI series, an inflation-breakeven
 * line without a second round-trip.
 *
 * <p>{@code bondCouponsReceivedTry} is the cumulative TRY coupon cash a bond holder had received by this date,
 * each past coupon priced at the per-period rate (.ORAN) in effect on its own payment date — a floater's
 * historical resets, not one flat rate. It is the single backend source for crediting coupons on the K/Z curve
 * (value drops on the ex-coupon date but the cash was received), so the client no longer reconstructs that math.
 */
public record FixedIncomeHistoryPoint(
        LocalDate date,
        BigDecimal depositValueTry,
        BigDecimal bondValueTry,
        BigDecimal totalValueTry,
        BigDecimal totalCostTry,
        BigDecimal bondCouponsReceivedTry
) {
}
