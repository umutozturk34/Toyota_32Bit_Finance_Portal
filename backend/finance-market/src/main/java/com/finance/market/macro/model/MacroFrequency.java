package com.finance.market.macro.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Publish cadence of a macro indicator; defines when the last observation is considered stale. */
public enum MacroFrequency {
    DAILY {
        @Override public boolean isStale(LocalDate lastObserved, LocalDate today) {
            return lastObserved == null || ChronoUnit.DAYS.between(lastObserved, today) >= 1;
        }
    },
    WEEKLY {
        @Override public boolean isStale(LocalDate lastObserved, LocalDate today) {
            return lastObserved == null || ChronoUnit.DAYS.between(lastObserved, today) >= 7;
        }
    },
    MONTHLY {
        @Override public boolean isStale(LocalDate lastObserved, LocalDate today) {
            return lastObserved == null
                    || lastObserved.getMonthValue() != today.getMonthValue()
                    || lastObserved.getYear() != today.getYear();
        }
    };

    public abstract boolean isStale(LocalDate lastObserved, LocalDate today);
}
