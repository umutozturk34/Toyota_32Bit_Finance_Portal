package com.finance.backend.model.value;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
