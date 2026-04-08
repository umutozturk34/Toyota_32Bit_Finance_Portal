package com.finance.backend.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class YahooRangePolicy {

    private YahooRangePolicy() {
    }

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
