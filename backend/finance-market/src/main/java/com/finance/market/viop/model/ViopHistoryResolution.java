package com.finance.market.viop.model;

import java.util.Map;

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

    public int periodMinutes() {
        return periodMinutes;
    }

    public static ViopHistoryResolution fromPeriodMinutes(int minutes) {
        ViopHistoryResolution r = BY_MINUTES.get(minutes);
        if (r == null) {
            throw new IllegalArgumentException("Unsupported VIOP history resolution: " + minutes + " min");
        }
        return r;
    }
}
