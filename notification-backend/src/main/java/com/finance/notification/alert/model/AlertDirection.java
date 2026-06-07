package com.finance.notification.alert.model;

import java.math.BigDecimal;

/**
 * Trigger condition for a price alert. Absolute directions ({@code ABOVE}/{@code BELOW}) compare the
 * current price to the threshold; percent directions compare the move from the reference price to a
 * percentage threshold. Each constant supplies its own {@link #isFired} predicate.
 */
public enum AlertDirection {
    ABOVE(true) {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold) {
            return currentPrice.compareTo(threshold) >= 0;
        }
    },
    BELOW(false) {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold) {
            return currentPrice.compareTo(threshold) <= 0;
        }
    },
    CHANGE_PCT_UP(true) {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal thresholdPct) {
            if (lastPrice == null || lastPrice.signum() == 0) return false;
            BigDecimal pct = currentPrice.subtract(lastPrice)
                    .divide(lastPrice, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
            return pct.compareTo(thresholdPct) >= 0;
        }
    },
    CHANGE_PCT_DOWN(false) {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal thresholdPct) {
            if (lastPrice == null || lastPrice.signum() == 0) return false;
            BigDecimal pct = currentPrice.subtract(lastPrice)
                    .divide(lastPrice, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
            return pct.compareTo(thresholdPct.negate()) <= 0;
        }
    };

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final boolean upward;

    AlertDirection(boolean upward) {
        this.upward = upward;
    }

    /** True for directions that fire on an upward move ({@code ABOVE}, {@code CHANGE_PCT_UP}). */
    public boolean isUpward() {
        return upward;
    }

    /** True when the threshold is a percentage move from the reference price rather than an absolute price. */
    public boolean isPercentBased() {
        return this == CHANGE_PCT_UP || this == CHANGE_PCT_DOWN;
    }

    /**
     * Whether the alert condition holds for this direction.
     *
     * @param lastPrice reference price for percent-based directions; ignored by absolute ones
     * @param threshold absolute price or percentage move depending on the direction
     */
    public abstract boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold);
}
