package com.finance.market.core.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Chooses the smallest Yahoo {@code range} token that still covers the gap since the last stored
 * candle, minimizing over-fetching; gaps beyond two years fall back to the configured max range.
 */
public final class YahooRangePolicy {

    private YahooRangePolicy() {
    }

    /** Range token covering the gap between the last candle and today in the given zone. */
    public static String fromLastCandle(LocalDateTime lastCandleDate, ZoneId zone, String maxRange) {
        long gapDays = ChronoUnit.DAYS.between(lastCandleDate.toLocalDate(), LocalDate.now(zone));
        return fromGapDays(gapDays, maxRange);
    }

    public static String fromGapDays(long gapDays, String maxRange) {
        if (gapDays <= 5) {
            return "5d";
        }
        if (gapDays <= 30) {
            return "1mo";
        }
        if (gapDays <= 90) {
            return "3mo";
        }
        if (gapDays <= 180) {
            return "6mo";
        }
        if (gapDays <= 365) {
            return "1y";
        }
        if (gapDays <= 730) {
            return "2y";
        }
        return maxRange;
    }
}
