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

    /**
     * Decides whether a fresh observation is overdue for this cadence. A {@code null}
     * {@code lastObserved} is always considered stale.
     *
     * @param lastObserved date of the most recent observation, or {@code null} if none
     * @param today        the reference date to measure staleness against
     * @return {@code true} if the gap since the last observation exceeds what this frequency permits
     */
    public abstract boolean isStale(LocalDate lastObserved, LocalDate today);
}
