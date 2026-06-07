package com.finance.market.viop.model;

import java.util.Map;

/** Candle bar interval for VIOP history, identified by its period in minutes. */
public enum ViopHistoryResolution {
    M5(5),
    M15(15),
    M30(30),
    H1(60),
    DAILY(1440);

    private static final Map<Integer, ViopHistoryResolution> BY_MINUTES = Map.of(
            5, M5,
            15, M15,
            30, M30,
            60, H1,
            1440, DAILY
    );

    private final int periodMinutes;

    ViopHistoryResolution(int periodMinutes) {
        this.periodMinutes = periodMinutes;
    }

    /** Bar length in minutes (e.g. {@code 60} for {@code H1}, {@code 1440} for {@code DAILY}). */
    public int periodMinutes() {
        return periodMinutes;
    }

    /** Maps a minute count to its resolution, throwing for unsupported intervals. */
    public static ViopHistoryResolution fromPeriodMinutes(int minutes) {
        ViopHistoryResolution r = BY_MINUTES.get(minutes);
        if (r == null) {
            throw new IllegalArgumentException("Unsupported VIOP history resolution: " + minutes + " min");
        }
        return r;
    }
}
