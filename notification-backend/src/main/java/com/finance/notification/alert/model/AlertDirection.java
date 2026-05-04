package com.finance.notification.alert.model;

import java.math.BigDecimal;

public enum AlertDirection {
    ABOVE {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold) {
            return currentPrice.compareTo(threshold) >= 0;
        }
    },
    BELOW {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold) {
            return currentPrice.compareTo(threshold) <= 0;
        }
    },
    CHANGE_PCT_UP {
        @Override
        public boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal thresholdPct) {
            if (lastPrice == null || lastPrice.signum() == 0) return false;
            BigDecimal pct = currentPrice.subtract(lastPrice)
                    .divide(lastPrice, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
            return pct.compareTo(thresholdPct) >= 0;
        }
    },
    CHANGE_PCT_DOWN {
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

    public abstract boolean isFired(BigDecimal currentPrice, BigDecimal lastPrice, BigDecimal threshold);
}
