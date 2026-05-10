package com.finance.notification.alert.model;

import java.math.BigDecimal;

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

    public boolean isUpward() {
        return upward;
    }

    public boolean isPercentBased() {
        return this == CHANGE_PCT_UP || this == CHANGE_PCT_DOWN;
    }

    public abstract boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold);
}
