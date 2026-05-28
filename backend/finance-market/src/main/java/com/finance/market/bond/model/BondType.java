package com.finance.market.bond.model;

/** Turkish bond classification (discount, fixed coupon, floating variants, sukuk); see {@link #isFloating()}. */
public enum BondType {
    DISCOUNTED,
    FIXED_COUPON,
    FLOATING_TLREF,
    FLOATING_CPI,
    FLOATING_AUCTION,
    SUKUK_FIXED,
    SUKUK_CPI;

    /** Whether the coupon resets over time (TLREF/CPI/auction floating, CPI-linked sukuk). */
    public boolean isFloating() {
        return this == FLOATING_TLREF || this == FLOATING_CPI
                || this == FLOATING_AUCTION || this == SUKUK_CPI;
    }
}
