package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to add or update a hypothetical Türkiye Hazine bond (tahvil/bono) holding. Bonds are ALWAYS
 * TRY, so there is no price-currency field: {@code entryPrice} is quoted in TRY per 100 nominal and
 * {@code quantity} is the nominal amount held. The bond is identified by its {@code bondSeriesCode};
 * the server resolves its ISIN/maturity from the market bond catalog before persisting.
 *
 * <p>Bounds mirror the persisted columns and the server-side {@code BondValidator} caps: the series code
 * is length/charset capped, amounts are positive and digit-bounded (quantity to 8 fraction digits matching
 * the nominal column, price to 4 matching the per-100 price column). {@code entryDate} is a plain
 * {@link LocalDate} (a calendar trade day, no intraday time), with future/min/maturity bounds enforced in
 * the validator rather than via {@code @PastOrPresent} so the violation carries a localized business key.
 *
 * <p>{@code couponRateOverride} is OPTIONAL and is the ANNUAL coupon percent the holder enters: the published
 * bond coupon (semi-annual) is only a suggestion, and the holder may overwrite it ("üstüne yazabilir") — the
 * override then persists on the holding. Null means "no override", so the response falls back to the published
 * rate annualized (× 2). It now FEEDS valuation via the accrued-coupon (işlemiş kupon) component of the dirty
 * value. {@code couponPaymentFrequency} is OPTIONAL (one of ANNUAL/SEMI_ANNUAL/QUARTERLY/MONTHLY/ZERO_COUPON,
 * default SEMI_ANNUAL) and drives the coupon cadence. The structural annotations only reject negative/absurdly-
 * scaled input; the configurable min/max cap and the frequency whitelist live in {@code BondValidator}.
 */
public record BondHoldingRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9._=-]{1,50}$") String bondSeriesCode,
        @NotNull @Positive @Digits(integer = 15, fraction = 8) BigDecimal quantity,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal entryPrice,
        @NotNull LocalDate entryDate,
        @PositiveOrZero @Digits(integer = 6, fraction = 4) BigDecimal couponRateOverride,
        @Size(max = 20) String couponPaymentFrequency
) {
}
