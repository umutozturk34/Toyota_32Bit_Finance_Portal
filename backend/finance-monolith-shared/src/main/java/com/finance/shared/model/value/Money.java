package com.finance.shared.model.value;

import com.finance.common.model.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Currency-tagged monetary value object. Immutable; amounts are normalized to {@link #SCALE}
 * decimals (HALF_UP) on construction. Arithmetic across differing currencies is rejected; use
 * {@link #inCurrency} to convert first.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final int SCALE = 4;

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Money amount cannot be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Money currency cannot be null");
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money of(long amount, Currency currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other, "plus");
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other, "minus");
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("multiply factor cannot be null");
        }
        return new Money(amount.multiply(factor), currency);
    }

    /**
     * Converts to {@code target} at the given positive rate; returns {@code this} unchanged when
     * already in the target currency.
     *
     * @throws IllegalArgumentException if the target is null or the rate is null/non-positive
     */
    public Money inCurrency(Currency target, BigDecimal rate) {
        if (target == null) {
            throw new IllegalArgumentException("target currency cannot be null");
        }
        if (target == currency) {
            return this;
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("conversion rate must be positive");
        }
        return new Money(amount.multiply(rate), target);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other, String op) {
        if (other == null) {
            throw new IllegalArgumentException("operand cannot be null");
        }
        if (other.currency != currency) {
            throw new IllegalArgumentException(
                    "Cannot " + op + " " + currency + " with " + other.currency);
        }
    }
}
