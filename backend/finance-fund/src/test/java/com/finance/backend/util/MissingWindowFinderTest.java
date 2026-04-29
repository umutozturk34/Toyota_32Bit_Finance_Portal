package com.finance.backend.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MissingWindowFinderTest {

    @Test
    void should_returnEmpty_when_allBusinessDaysExist() {
        LocalDate from = LocalDate.of(2026, 4, 27);
        LocalDate to = LocalDate.of(2026, 4, 29);
        List<LocalDate> existing = List.of(
                LocalDate.of(2026, 4, 27),
                LocalDate.of(2026, 4, 28),
                LocalDate.of(2026, 4, 29));

        List<WindowedFetchPlanner.DateWindow> windows = MissingWindowFinder.findMissingWindows(
                List.of(existing), from, to, 30);

        assertThat(windows).isEmpty();
    }

    @Test
    void should_skipWeekends_when_computingMissing() {
        LocalDate from = LocalDate.of(2026, 4, 25);
        LocalDate to = LocalDate.of(2026, 4, 27);

        List<WindowedFetchPlanner.DateWindow> windows = MissingWindowFinder.findMissingWindows(
                List.of(List.of()), from, to, 30);

        assertThat(windows).hasSize(1);
        assertThat(windows.getFirst().start()).isEqualTo(LocalDate.of(2026, 4, 27));
        assertThat(windows.getFirst().end()).isEqualTo(LocalDate.of(2026, 4, 27));
    }

    @Test
    void should_unionMissingDates_when_multipleFundsHaveDifferentGaps() {
        LocalDate from = LocalDate.of(2026, 4, 27);
        LocalDate to = LocalDate.of(2026, 4, 29);
        List<LocalDate> fundA = List.of(LocalDate.of(2026, 4, 27), LocalDate.of(2026, 4, 28));
        List<LocalDate> fundB = List.of(LocalDate.of(2026, 4, 28), LocalDate.of(2026, 4, 29));

        List<WindowedFetchPlanner.DateWindow> windows = MissingWindowFinder.findMissingWindows(
                List.of(fundA, fundB), from, to, 30);

        assertThat(windows).hasSize(1);
        assertThat(windows.getFirst().start()).isEqualTo(LocalDate.of(2026, 4, 27));
        assertThat(windows.getFirst().end()).isEqualTo(LocalDate.of(2026, 4, 29));
    }

    @Test
    void should_splitIntoSeparateWindows_when_gapBetweenMissingRangesExceeds7Days() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        Set<LocalDate> existing = bulkBusinessDays(from, to);
        existing.removeAll(Set.of(
                LocalDate.of(2026, 1, 8),
                LocalDate.of(2026, 1, 9),
                LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 3, 17)));

        List<WindowedFetchPlanner.DateWindow> windows = MissingWindowFinder.findMissingWindows(
                List.of(existing), from, to, 30);

        assertThat(windows).hasSize(2);
        assertThat(windows.get(0).start()).isEqualTo(LocalDate.of(2026, 1, 8));
        assertThat(windows.get(1).start()).isEqualTo(LocalDate.of(2026, 3, 16));
    }

    @Test
    void should_chunkIntoMaxDaysPerChunk_when_missingRangeExceedsLimit() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 4, 30);

        List<WindowedFetchPlanner.DateWindow> windows = MissingWindowFinder.findMissingWindows(
                List.of(List.of()), from, to, 30);

        assertThat(windows.size()).isGreaterThanOrEqualTo(3);
        for (WindowedFetchPlanner.DateWindow w : windows) {
            long span = java.time.temporal.ChronoUnit.DAYS.between(w.start(), w.end());
            assertThat(span).isLessThan(30);
        }
    }

    private Set<LocalDate> bulkBusinessDays(LocalDate from, LocalDate to) {
        Set<LocalDate> dates = new java.util.HashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() < 6) dates.add(d);
        }
        return dates;
    }
}
