package com.finance.portfolio.scheduler;

import com.finance.portfolio.service.PortfolioSnapshotService;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotSchedulerTest {

    @Mock private PortfolioSnapshotService snapshotService;
    @Mock private TaskTrackingService taskTracker;

    private PortfolioSnapshotScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PortfolioSnapshotScheduler(snapshotService, taskTracker);
        doAnswer(inv -> { ((Runnable) inv.getArgument(2)).run(); return null; })
                .when(taskTracker).runTracked(any(), any(), any());
    }

    @Test
    void runMorningSnapshot_runsTrackedTaskWithMorningSource() {
        scheduler.runMorningSnapshot();

        verify(taskTracker).runTracked(eq("scheduled-portfolio-snapshot-morning"),
                eq("Morning portfolio snapshot"), any(Runnable.class));
        verify(snapshotService).generateDailySnapshots("morning");
    }

    @Test
    void runEveningSnapshot_runsTrackedTaskWithEveningSource() {
        scheduler.runEveningSnapshot();

        verify(taskTracker).runTracked(eq("scheduled-portfolio-snapshot-evening"),
                eq("Evening portfolio snapshot"), any(Runnable.class));
        verify(snapshotService).generateDailySnapshots("evening");
    }
}
