package com.finance.market.bond.model;

/**
 * Türkiye Hazine instrument classification, mirroring the TCMB DİBS / kira sertifikası type sections:
 * a zero-coupon discount bill, a fixed-coupon bond, the floating variants (TLREF-/CPI-/auction-reset),
 * gold-linked (altına dayalı) securities, and the sukuk (kira sertifikası) family (fixed, floating, CPI,
 * gold). Classification is inferred from coupon, value scale and rate-change behaviour — see
 * {@code BondTypeResolver}. Valuation/coupon behaviour is keyed off {@link #isCpiLinked()},
 * {@link #isGoldLinked()} and {@link #isFloating()}.
 */
public enum BondType {
    DISCOUNTED,
    FIXED_COUPON,
    FLOATING_TLREF,
    FLOATING_CPI,
    FLOATING_AUCTION,
    GOLD,
    SUKUK_FIXED,
    SUKUK_FLOATING,
    SUKUK_CPI,
    SUKUK_GOLD;

    /** Whether the coupon resets over time (TLREF/CPI/auction floating, floating/CPI sukuk). */
    public boolean isFloating() {
        return this == FLOATING_TLREF || this == FLOATING_CPI || this == FLOATING_AUCTION
                || this == SUKUK_FLOATING || this == SUKUK_CPI;
    }

    /**
     * Whether this is an inflation-(CPI-)indexed bond. Its quoted clean price is the per-100 indexed price (≈100
     * at issue, growing with cumulative inflation), and its small REAL coupon accrues on the inflation-indexed
     * principal (not a plain face), so coupon math uses the indexed value as the base. TLREF/auction/floating-sukuk
     * are NOT index-linked — their price orbits par and their coupon IS the return, accruing on the face nominal.
     */
    public boolean isCpiLinked() {
        return this == FLOATING_CPI || this == SUKUK_CPI;
    }

    /**
     * Whether this is a non-CPI floater whose coupon rides the FACE nominal at a per-period rate that resets each
     * period (TLREF, auction, or floating sukuk). Their price orbits par, so the coupon — read from the rate
     * history per period — is the return; they accrue and settle at par like a fixed bond. CPI floaters are
     * excluded (their coupon rides the inflation-indexed principal, not the face).
     */
    public boolean isParFloater() {
        return this == FLOATING_TLREF || this == FLOATING_AUCTION || this == SUKUK_FLOATING;
    }

    /**
     * Whether this is a gold-linked (altına dayalı) security. Its quoted value is the gold-content value of ONE
     * certificate (grams × gold price, in the thousands of TRY) — quoted PER UNIT, not per 100 nominal — so it is
     * valued as {@code price × quantity} and redeems at its gold value rather than par. See {@link #isPerUnit()}.
     */
    public boolean isGoldLinked() {
        return this == GOLD || this == SUKUK_GOLD;
    }

    /**
     * Whether the instrument is quoted PER UNIT (one certificate) rather than per 100 nominal. Only gold-linked
     * securities are per-unit; every other type is per-100. Drives whether position value divides by 100.
     */
    public boolean isPerUnit() {
        return isGoldLinked();
    }
}
