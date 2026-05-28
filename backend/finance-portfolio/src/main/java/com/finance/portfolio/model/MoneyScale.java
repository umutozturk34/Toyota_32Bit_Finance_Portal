package com.finance.portfolio.model;

/**
 * Shared rounding scale for monetary/percentage {@code BigDecimal} math across the module, so all
 * persisted TRY figures and ratios use a consistent 4-decimal precision.
 */
public final class MoneyScale {

    public static final int PRICE = 4;

    private MoneyScale() {}
}
