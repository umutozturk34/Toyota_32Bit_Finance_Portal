package com.finance.shared.model.value;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single-currency (TRY) monetary value object for portfolio math. Immutable; amounts are normalized
 * to {@link #SCALE} decimals (HALF_UP) on construction. Unlike {@link Money} it carries no currency
 * tag, so operations are unconditionally combinable.
 */
public record MoneyTRY(BigDecimal amount) implements Comparable<MoneyTRY> {

    public static final int SCALE = 4;
    public static final MoneyTRY ZERO = new MoneyTRY(BigDecimal.ZERO);

    public MoneyTRY {
        if (amount == null) {
            throw new IllegalArgumentException("MoneyTRY amount cannot be null");
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static MoneyTRY of(BigDecimal amount) {
        return new MoneyTRY(amount);
    }

    public static MoneyTRY of(String amount) {
        return new MoneyTRY(new BigDecimal(amount));
    }

    public static MoneyTRY of(long amount) {
        return new MoneyTRY(BigDecimal.valueOf(amount));
    }

    public MoneyTRY plus(MoneyTRY other) {
        return new MoneyTRY(amount.add(other.amount));
    }

    public MoneyTRY minus(MoneyTRY other) {
        return new MoneyTRY(amount.subtract(other.amount));
    }

    public MoneyTRY multiply(BigDecimal factor) {
        return new MoneyTRY(amount.multiply(factor));
    }

    /** Divides at {@link #SCALE} precision (HALF_UP). */
    public MoneyTRY divide(BigDecimal divisor) {
        return new MoneyTRY(amount.divide(divisor, SCALE, RoundingMode.HALF_UP));
    }

    public MoneyTRY negate() {
        return new MoneyTRY(amount.negate());
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

    public boolean isGreaterThanOrEqualTo(MoneyTRY other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(MoneyTRY other) {
        return amount.compareTo(other.amount);
    }
}
