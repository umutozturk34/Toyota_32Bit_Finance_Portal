package com.finance.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes absolute and percentage change between a current and previous value, used for daily
 * change displays. Guards against null inputs and division by zero by returning {@link Result#EMPTY}.
 */
public final class PercentChangeCalculator {

    /** Absolute change amount and percentage; both null in the {@link #EMPTY} sentinel. */
    public record Result(BigDecimal amount, BigDecimal percent) {
        public static final Result EMPTY = new Result(null, null);
    }

    private PercentChangeCalculator() {}

    /** Returns change at the given scale, or {@link Result#EMPTY} if either value is null or previous is zero. */
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
