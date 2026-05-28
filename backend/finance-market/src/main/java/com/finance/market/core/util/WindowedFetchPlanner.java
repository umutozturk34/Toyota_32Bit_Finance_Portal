package com.finance.market.core.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a date range into contiguous, non-overlapping windows of at most a given size, used to
 * page external fetches that cap the span per request.
 */
public final class WindowedFetchPlanner {

    private WindowedFetchPlanner() {
    }

    /** A single inclusive fetch window. */
    public record DateWindow(LocalDate start, LocalDate end) {
    }

    /** Windows from oldest to newest, each up to {@code maxDays} long, covering {@code [startDate, endDate]}. */
    public static List<DateWindow> planForward(LocalDate startDate, LocalDate endDate, int maxDays) {
        List<DateWindow> windows = new ArrayList<>();
        LocalDate windowStart = startDate;
        while (windowStart.isBefore(endDate) || windowStart.isEqual(endDate)) {
            LocalDate windowEnd = windowStart.plusDays(maxDays - 1L);
            if (windowEnd.isAfter(endDate)) {
                windowEnd = endDate;
            }
            windows.add(new DateWindow(windowStart, windowEnd));
            windowStart = windowEnd.plusDays(1);
        }
        return windows;
    }

    /** Windows from newest back to {@code limitDate}, for back-fills that walk earlier in time. */
    public static List<DateWindow> planBackward(LocalDate limitDate, LocalDate endDate, int windowSize) {
        List<DateWindow> windows = new ArrayList<>();
        LocalDate currentWindowEnd = endDate;

        while (currentWindowEnd.isAfter(limitDate)) {
            LocalDate currentWindowStart = currentWindowEnd.minusDays(windowSize);
            if (currentWindowStart.isBefore(limitDate)) {
                currentWindowStart = limitDate;
            }

            windows.add(new DateWindow(currentWindowStart, currentWindowEnd));
            currentWindowEnd = currentWindowStart.minusDays(1);
        }

        return windows;
    }
}
