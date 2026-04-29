package com.finance.backend.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class MissingWindowFinder {

    private MissingWindowFinder() {}

    public static List<WindowedFetchPlanner.DateWindow> findMissingWindows(
            Collection<? extends Collection<LocalDate>> existingDatesPerFund,
            LocalDate from, LocalDate to, int maxDaysPerChunk) {
        if (!from.isBefore(to) && !from.isEqual(to)) {
            return List.of();
        }
        Set<LocalDate> missing = collectMissingBusinessDays(existingDatesPerFund, from, to);
        if (missing.isEmpty()) {
            return List.of();
        }
        return chunkConsecutive(missing, maxDaysPerChunk);
    }

    private static Set<LocalDate> collectMissingBusinessDays(
            Collection<? extends Collection<LocalDate>> existingDatesPerFund,
            LocalDate from, LocalDate to) {
        Set<LocalDate> union = new TreeSet<>();
        for (Collection<LocalDate> existing : existingDatesPerFund) {
            Set<LocalDate> existingSet = new HashSet<>(existing);
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (isWeekend(date)) continue;
                if (!existingSet.contains(date)) {
                    union.add(date);
                }
            }
        }
        return union;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    private static List<WindowedFetchPlanner.DateWindow> chunkConsecutive(
            Set<LocalDate> missingDates, int maxDaysPerChunk) {
        List<LocalDate> sorted = new ArrayList<>(missingDates);
        List<WindowedFetchPlanner.DateWindow> windows = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            LocalDate rangeStart = sorted.get(i);
            LocalDate rangeEnd = rangeStart;
            int j = i + 1;
            while (j < sorted.size()
                    && !sorted.get(j).isAfter(rangeEnd.plusDays(7))
                    && !sorted.get(j).isAfter(rangeStart.plusDays(maxDaysPerChunk - 1L))) {
                rangeEnd = sorted.get(j);
                j++;
            }
            windows.add(new WindowedFetchPlanner.DateWindow(rangeStart, rangeEnd));
            i = j;
        }
        return windows;
    }
}
