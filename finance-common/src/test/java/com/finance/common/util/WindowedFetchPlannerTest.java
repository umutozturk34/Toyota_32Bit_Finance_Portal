package com.finance.common.util;

import com.finance.common.util.WindowedFetchPlanner.DateWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WindowedFetchPlannerTest {

    @Test
    void planForwardSingleWindowWhenRangeFitsMaxDays() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 10);

        List<DateWindow> windows = WindowedFetchPlanner.planForward(start, end, 30);

        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).start()).isEqualTo(start);
        assertThat(windows.get(0).end()).isEqualTo(end);
    }

    @Test
    void planForwardSplitsIntoMultipleWindows() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 20);

        List<DateWindow> windows = WindowedFetchPlanner.planForward(start, end, 7);

        assertThat(windows).hasSize(3);
        assertThat(windows.get(0).start()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(windows.get(0).end()).isEqualTo(LocalDate.of(2025, 1, 7));
        assertThat(windows.get(1).start()).isEqualTo(LocalDate.of(2025, 1, 8));
        assertThat(windows.get(1).end()).isEqualTo(LocalDate.of(2025, 1, 14));
        assertThat(windows.get(2).start()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(windows.get(2).end()).isEqualTo(LocalDate.of(2025, 1, 20));
    }

    @Test
    void planForwardStartEqualsEnd() {
        LocalDate date = LocalDate.of(2025, 6, 15);

        List<DateWindow> windows = WindowedFetchPlanner.planForward(date, date, 10);

        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).start()).isEqualTo(date);
        assertThat(windows.get(0).end()).isEqualTo(date);
    }

    @Test
    void planForwardLastWindowClampedToEnd() {
        LocalDate start = LocalDate.of(2025, 3, 1);
        LocalDate end = LocalDate.of(2025, 3, 12);

        List<DateWindow> windows = WindowedFetchPlanner.planForward(start, end, 10);

        assertThat(windows).hasSize(2);
        assertThat(windows.get(1).end()).isEqualTo(end);
    }

    @Test
    void planBackwardSingleWindowWhenRangeFitsWindowSize() {
        LocalDate limit = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 5);

        List<DateWindow> windows = WindowedFetchPlanner.planBackward(limit, end, 30);

        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).start()).isEqualTo(limit);
        assertThat(windows.get(0).end()).isEqualTo(end);
    }

    @Test
    void planBackwardSplitsIntoMultipleWindows() {
        LocalDate limit = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 25);

        List<DateWindow> windows = WindowedFetchPlanner.planBackward(limit, end, 10);

        assertThat(windows).hasSize(3);
        assertThat(windows.get(0).end()).isEqualTo(LocalDate.of(2025, 1, 25));
        assertThat(windows.get(0).start()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(windows.get(1).end()).isEqualTo(LocalDate.of(2025, 1, 14));
        assertThat(windows.get(1).start()).isEqualTo(LocalDate.of(2025, 1, 4));
        assertThat(windows.get(2).end()).isEqualTo(LocalDate.of(2025, 1, 3));
        assertThat(windows.get(2).start()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void planBackwardStartClampedToLimit() {
        LocalDate limit = LocalDate.of(2025, 1, 5);
        LocalDate end = LocalDate.of(2025, 1, 20);

        List<DateWindow> windows = WindowedFetchPlanner.planBackward(limit, end, 10);

        DateWindow lastWindow = windows.get(windows.size() - 1);
        assertThat(lastWindow.start()).isAfterOrEqualTo(limit);
    }

    @Test
    void planBackwardEndEqualsLimit() {
        LocalDate date = LocalDate.of(2025, 6, 15);

        List<DateWindow> windows = WindowedFetchPlanner.planBackward(date, date, 10);

        assertThat(windows).isEmpty();
    }
}
