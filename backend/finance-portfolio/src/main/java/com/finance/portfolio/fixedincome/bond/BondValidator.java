package com.finance.portfolio.fixedincome.bond;

import com.finance.common.exception.BusinessException;
import com.finance.market.bond.model.Bond;
import com.finance.portfolio.config.PortfolioProperties.BondLimits;
import com.finance.portfolio.dto.request.BondHoldingRequest;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Stateless guards for bond-holding commands. Mirrors {@code PortfolioValidator}: static {@code validate*}
 * methods that throw localized {@link BusinessException}s on violation, with caps sourced from
 * {@link BondLimits}. Bonds are ALWAYS TRY, so the price bounds apply directly to the supplied TRY price
 * with no currency conversion. Existence is validated against a resolved {@link Bond} (the caller looks it
 * up by series code first); the entry date must fall within the bond's life, i.e. before its maturity end.
 */
@Log4j2
final class BondValidator {

    private BondValidator() {
    }

    /**
     * Validates a new/updated bond holding against quantity, price, entry-date and maturity bounds. The
     * {@code bond} is the resolved market record for {@code request.bondSeriesCode()}; passing {@code null}
     * means the series code did not resolve and the holding is rejected as a non-existent bond.
     */
    static void validate(BondHoldingRequest request, Bond bond, BondLimits limits) {
        log.debug("Validating bond holding: series={} qty={} price={} entryDate={}",
                request.bondSeriesCode(), request.quantity(), request.entryPrice(), request.entryDate());
        if (bond == null) {
            throw new BusinessException("error.portfolio.bond.notFound", request.bondSeriesCode());
        }
        validateQuantity(request.quantity(), limits);
        validatePrice(request.entryPrice(), limits);
        validateEntryDate(request.entryDate(), bond, limits);
        validateCouponRate(request.couponRateOverride(), limits);
        validateCouponFrequency(request.couponPaymentFrequency());
    }

    /**
     * Bounds the optional coupon payment frequency to the known {@link CouponFrequency} set. Null/blank is
     * allowed (it defaults to SEMI_ANNUAL); an unknown value is rejected with a localized key rather than
     * silently coerced, so a typo never persists a misleading cadence.
     */
    private static void validateCouponFrequency(String frequency) {
        if (frequency == null || frequency.isBlank()) {
            return;
        }
        try {
            CouponFrequency.valueOf(frequency.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("error.portfolio.bond.couponFrequencyInvalid", frequency);
        }
    }

    /**
     * Bounds the optional user coupon-rate override against {@link BondLimits}. A null override is allowed (it
     * means "use the bond's published rate") and skips the check entirely. The cap is configurable so a mistyped
     * value (e.g. a price pasted into the coupon field) cannot persist an absurd display rate. Coupon is
     * informational only and never feeds valuation, but it must still be a sane percentage to render correctly.
     */
    private static void validateCouponRate(BigDecimal couponRateOverride, BondLimits limits) {
        if (couponRateOverride == null) {
            return;
        }
        boolean belowMin = limits.getMinCouponRate() != null
                && couponRateOverride.compareTo(limits.getMinCouponRate()) < 0;
        boolean aboveMax = limits.getMaxCouponRate() != null
                && couponRateOverride.compareTo(limits.getMaxCouponRate()) > 0;
        if (belowMin || aboveMax) {
            throw new BusinessException("error.portfolio.bond.couponOutOfRange",
                    limits.getMinCouponRate(), limits.getMaxCouponRate());
        }
    }

    private static void validateQuantity(BigDecimal quantity, BondLimits limits) {
        if (limits.getMinQuantity() != null && quantity.compareTo(limits.getMinQuantity()) < 0) {
            throw new BusinessException("error.portfolio.bond.quantityTooLow", limits.getMinQuantity());
        }
        if (limits.getMaxQuantity() != null && quantity.compareTo(limits.getMaxQuantity()) > 0) {
            throw new BusinessException("error.portfolio.bond.quantityTooHigh", limits.getMaxQuantity());
        }
    }

    /**
     * Bounds a TRY clean price (per 100 nominal) against {@link BondLimits}. Package-visible so the exit
     * (sell) path can reuse the same min/max guard as add/update — a negative or absurd exit price would
     * otherwise produce a corrupted negative realized value/PnL. A null price is rejected as too-low.
     */
    static void validatePrice(BigDecimal price, BondLimits limits) {
        if (price == null
                || (limits.getMinPriceTry() != null && price.compareTo(limits.getMinPriceTry()) < 0)) {
            throw new BusinessException("error.portfolio.bond.priceTooLow", limits.getMinPriceTry());
        }
        if (limits.getMaxPriceTry() != null && price.compareTo(limits.getMaxPriceTry()) > 0) {
            throw new BusinessException("error.portfolio.bond.priceTooHigh", limits.getMaxPriceTry());
        }
    }

    private static void validateEntryDate(LocalDate entryDate, Bond bond, BondLimits limits) {
        if (entryDate.isAfter(LocalDate.now())) {
            throw new BusinessException("error.portfolio.bond.entryDateInFuture");
        }
        if (limits.getMinEntryDate() != null && entryDate.isBefore(limits.getMinEntryDate())) {
            throw new BusinessException("error.portfolio.bond.entryDateTooOld", limits.getMinEntryDate());
        }
        // The holding must be entered while the bond is still alive: an entry on/after maturity end has no
        // remaining term to value, so it is rejected as already-matured rather than silently held at par.
        LocalDate maturityEnd = bond.getMaturityEnd();
        if (maturityEnd != null && !entryDate.isBefore(maturityEnd)) {
            throw new BusinessException("error.portfolio.bond.alreadyMatured", bond.getSeriesCode());
        }
    }
}
