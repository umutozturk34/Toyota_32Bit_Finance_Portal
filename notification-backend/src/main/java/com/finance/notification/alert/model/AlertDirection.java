package com.finance.notification.alert.model;

import java.math.BigDecimal;

public enum AlertDirection {
    ABOVE("Eşiğin üstüne çıktı", true) {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold) {
            return currentPrice.compareTo(threshold) >= 0;
        }
    },
    BELOW("Eşiğin altına düştü", false) {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold) {
            return currentPrice.compareTo(threshold) <= 0;
        }
    },
    CHANGE_PCT_UP("Yüzde olarak yükseldi", true) {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal thresholdPct) {
            if (lastPrice == null || lastPrice.signum() == 0) return false;
            BigDecimal pct = currentPrice.subtract(lastPrice)
                    .divide(lastPrice, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
            return pct.compareTo(thresholdPct) >= 0;
        }
    },
    CHANGE_PCT_DOWN("Yüzde olarak düştü", false) {
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

    private final String displayLabel;
    private final boolean upward;

    AlertDirection(String displayLabel, boolean upward) {
        this.displayLabel = displayLabel;
        this.upward = upward;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public boolean isUpward() {
        return upward;
    }

    public boolean isPercentBased() {
        return this == CHANGE_PCT_UP || this == CHANGE_PCT_DOWN;
    }

    public abstract boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold);
}
