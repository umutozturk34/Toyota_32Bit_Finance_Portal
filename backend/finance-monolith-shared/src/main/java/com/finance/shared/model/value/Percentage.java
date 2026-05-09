package com.finance.shared.model.value;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Percentage(BigDecimal value) implements Comparable<Percentage> {

    public static final int SCALE = 4;
    public static final Percentage ZERO = new Percentage(BigDecimal.ZERO);

    public Percentage {
        if (value == null) {
            throw new IllegalArgumentException("Percentage value cannot be null");
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }

    public static Percentage of(String value) {
        return new Percentage(new BigDecimal(value));
    }

    public static Percentage ofRatio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return ZERO;
        }
        BigDecimal ratio = numerator.divide(denominator, SCALE + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return new Percentage(ratio);
    }

    public BigDecimal asFraction() {
        return value.divide(BigDecimal.valueOf(100), SCALE + 2, RoundingMode.HALF_UP);
    }

    public MoneyTRY applyTo(MoneyTRY amount) {
        return amount.multiply(asFraction());
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public boolean isNegative() {
        return value.signum() < 0;
    }

    @Override
    public int compareTo(Percentage other) {
        return value.compareTo(other.value);
    }
}
