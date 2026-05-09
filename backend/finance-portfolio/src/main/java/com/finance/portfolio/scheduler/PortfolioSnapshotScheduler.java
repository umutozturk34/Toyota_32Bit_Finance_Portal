package com.finance.portfolio.scheduler;

import com.finance.portfolio.service.PortfolioSnapshotService;
import com.finance.shared.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class PortfolioSnapshotScheduler {

    private final PortfolioSnapshotService snapshotService;
    private final TaskTrackingService taskTracker;

    @Scheduled(cron = "${app.scheduler.portfolio.snapshot-cron}", zone = "${app.timezone}")
    public void runDailySnapshots() {
        taskTracker.runTracked("scheduled-portfolio-snapshot",
                "Daily portfolio snapshot generation",
                snapshotService::generateDailySnapshots);
    }
}
