package com.finance.backend.scheduler;

import com.finance.backend.service.PortfolioSnapshotService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
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

    @Scheduled(cron = "0 0 23 * * *", zone = "${app.timezone}")
    public void runDailySnapshots() {
        TaskInfo started = taskTracker.startTask("scheduled-portfolio-snapshot", "Daily portfolio snapshot generation");
        try {
            snapshotService.generateDailySnapshots();
            taskTracker.completeTask("scheduled-portfolio-snapshot", started);
        } catch (Exception e) {
            log.error("Portfolio snapshot generation failed", e);
            taskTracker.failTask("scheduled-portfolio-snapshot", started, e.getMessage());
        }
    }
}
