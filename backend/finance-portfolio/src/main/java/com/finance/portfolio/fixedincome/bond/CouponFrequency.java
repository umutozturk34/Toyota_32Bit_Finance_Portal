package com.finance.portfolio.fixedincome.bond;

import com.finance.market.bond.model.BondType;

/**
 * How often a bond/bill pays its coupon, chosen per holding. The user enters an ANNUAL coupon rate and a
 * frequency; the per-period coupon is {@code annualRate / paymentsPerYear} and coupon dates step from the
 * bond's issue date ({@code maturityStart}) by {@link #stepMonths()}. {@link #ZERO_COUPON} marks a discount
 * bill (no periodic coupon) so accrued-coupon math is skipped entirely.
 */
public enum CouponFrequency {

    ANNUAL(1),
    SEMI_ANNUAL(2),
    QUARTERLY(4),
    MONTHLY(12),
    ZERO_COUPON(0);

    /** The fixed cadence assumed when a holding does not specify one (Türkiye Hazine standard). */
    public static final CouponFrequency DEFAULT = SEMI_ANNUAL;

    private final int paymentsPerYear;

    CouponFrequency(int paymentsPerYear) {
        this.paymentsPerYear = paymentsPerYear;
    }

    /** Coupons paid per year (0 for a zero-coupon bill). */
    public int paymentsPerYear() {
        return paymentsPerYear;
    }

    /** Whether this frequency pays a periodic coupon at all. */
    public boolean paysCoupon() {
        return paymentsPerYear > 0;
    }

    /** Months between coupon dates (0 for a zero-coupon bill). */
    public int stepMonths() {
        return paymentsPerYear == 0 ? 0 : 12 / paymentsPerYear;
    }

    /**
     * The cadence to assume for a bond when the holder doesn't pick one, inferred from its {@link BondType}:
     * a discount bill pays no coupon (ZERO_COUPON); a TLREF floater resets/pays quarterly; everything else
     * follows the Türkiye Hazine semi-annual standard.
     */
    public static CouponFrequency defaultFor(BondType type) {
        if (type == null) {
            return DEFAULT;
        }
        if (type == BondType.DISCOUNTED) {
            return ZERO_COUPON;
        }
        if (type == BondType.FLOATING_TLREF) {
            return QUARTERLY;
        }
        return SEMI_ANNUAL;
    }

    /**
     * The frequency whose coupon period is {@code months} (1→MONTHLY, 3→QUARTERLY, 6→SEMI_ANNUAL, 12→ANNUAL),
     * or {@code null} when {@code months} is non-positive or not a standard period. Used to map a cadence
     * inferred from a bond's price-drop spacing back onto the enum.
     */
    public static CouponFrequency fromStepMonths(int months) {
        if (months <= 0) {
            return null;
        }
        int perYear = 12 / months;
        for (CouponFrequency frequency : values()) {
            if (frequency.paymentsPerYear == perYear && frequency.stepMonths() == months) {
                return frequency;
            }
        }
        return null;
    }

    /** Parses a frequency name case-insensitively, falling back to {@link #DEFAULT} for null/blank/unknown. */
    public static CouponFrequency fromNullable(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DEFAULT;
        }
    }
}
