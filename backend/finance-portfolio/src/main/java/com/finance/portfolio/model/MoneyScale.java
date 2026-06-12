package com.finance.portfolio.model;

/**
 * Shared rounding scale for monetary/percentage {@code BigDecimal} math across the module. Set to 8
 * decimals so tiny fractional-unit positions (e.g. 0.000001 of a crypto whose unit price is small)
 * keep a non-zero market value / P&amp;L instead of collapsing to 0 — a 4-decimal scale rounded a
 * ~0.00004 TRY position down to 0,00. Display layers cap the visible decimals per magnitude, so normal
 * figures are unaffected; only sub-0.0001 values now survive the math.
 */
public final class MoneyScale {

    public static final int PRICE = 8;

    private MoneyScale() {}
}
