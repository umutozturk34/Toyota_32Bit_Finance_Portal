package com.finance.market.bond.model;

public enum BondType {
    DISCOUNTED,
    FIXED_COUPON,
    FLOATING_TLREF,
    FLOATING_CPI,
    FLOATING_AUCTION,
    SUKUK_FIXED,
    SUKUK_CPI;

    public boolean isFloating() {
        return this == FLOATING_TLREF || this == FLOATING_CPI
                || this == FLOATING_AUCTION || this == SUKUK_CPI;
    }
}
