package com.finance.common.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class WindowedFetchPlanner {

    private WindowedFetchPlanner() {
    }

    public record DateWindow(LocalDate start, LocalDate end) {
    }

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
