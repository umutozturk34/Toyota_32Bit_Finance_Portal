package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A bond holding row for the fixed-income grid: identity, the resolved bond reference (series/ISIN/name),
 * entry and optional exit, and live valuation in TRY. Bonds are ALWAYS TRY, so every monetary figure here
 * is TRY. {@code currentPriceTry} is the clean price per 100 nominal; {@code currentValueTry} and
 * {@code costTry} are full-position TRY amounts; {@code pnlTry}/{@code pnlPercent} are derived against cost.
 *
 * <p>Valuation is CLEAN: {@code currentValueTry} == {@code nominalValueTry} (clean price × nominal) so the
 * grid/summary/history reconcile and {@code pnlTry} is a pure mark-to-market figure. The işlemiş kupon since the
 * last coupon date is surfaced SEPARATELY as {@code accruedCouponTry} (NOT folded into the value), and
 * {@code dailyCouponAccrualTry} is how much coupon the position accrues per calendar day in the current period, so
 * the UI can present the dirty value (nominal + accrued) itself. A closed holding reports its realized clean value
 * (no accrued line). To get the dirty/settlement price, add {@code accruedCouponTry} to {@code currentValueTry}.
 *
 * <p>The reference block ({@code couponRate}..{@code couponFrequency}) is denormalized from the resolved market
 * bond so the grid and chart can render coupon/maturity context without a second round-trip. {@code couponRate}
 * is the EFFECTIVE ANNUAL coupon the UI displays — the holder's {@code couponRateOverride} (already annual) when
 * set, otherwise the bond's published rate annualized (× 2). {@code publishedCouponRate} is the bond's own ANNUAL
 * rate INDEPENDENT of any override (null when the series no longer resolves). {@code couponRateOverride} is the
 * raw stored annual override (null when unset) and {@code couponOverridden} mirrors whether it is non-null.
 * {@code couponFrequency} is this holding's chosen cadence (e.g. {@code "SEMI_ANNUAL"}). Reference fields are
 * null-tolerant: when the series no longer resolves they are left null rather than failing the row.
 *
 * <p>{@code redeemed} is true when a non-CPI bond has reached maturity while still open: the issuer has repaid its
 * face, so the position is auto-settled at par and the UI presents it as closed-by-redemption (no sell action).
 */
public record BondHoldingResponse(
        Long id,
        String bondSeriesCode,
        String bondIsin,
        String bondName,
        BigDecimal quantity,
        BigDecimal entryPrice,
        LocalDate entryDate,
        LocalDate exitDate,
        BigDecimal exitPrice,
        BigDecimal currentPriceTry,
        BigDecimal currentValueTry,
        BigDecimal nominalValueTry,
        BigDecimal accruedCouponTry,
        BigDecimal dailyCouponAccrualTry,
        BigDecimal couponsReceivedTry,
        int couponsReceivedCount,
        BigDecimal costTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent,
        BigDecimal couponRate,
        BigDecimal publishedCouponRate,
        BigDecimal couponRateOverride,
        boolean couponOverridden,
        LocalDate maturityStart,
        LocalDate maturityEnd,
        LocalDate lastCouponDate,
        LocalDate nextCouponDate,
        String bondType,
        String couponFrequency,
        boolean redeemed
) {
}
