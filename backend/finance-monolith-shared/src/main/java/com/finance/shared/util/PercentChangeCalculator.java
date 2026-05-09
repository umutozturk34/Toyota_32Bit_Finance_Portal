package com.finance.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PercentChangeCalculator {

    public record Result(BigDecimal amount, BigDecimal percent) {
        public static final Result EMPTY = new Result(null, null);
    }

    private PercentChangeCalculator() {}

    public static Result compute(BigDecimal current, BigDecimal previous, int scale) {
        if (current == null || previous == null || previous.signum() == 0) {
            return Result.EMPTY;
        }
        BigDecimal change = current.subtract(previous);
        BigDecimal amount = change.setScale(scale, RoundingMode.HALF_UP);
        BigDecimal percent = change.divide(previous, scale + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(scale, RoundingMode.HALF_UP);
        return new Result(amount, percent);
    }
}
