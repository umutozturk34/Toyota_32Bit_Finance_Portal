package com.finance.backend.scheduler;

import com.finance.backend.service.BondDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class BondScheduler {
    private final BondDataService bondDataService;
    private final TaskTrackingService taskTracker;

    @Scheduled(cron = "${app.scheduler.bond.daily-cron}", zone = "${app.timezone}")
    public void runDailyBondUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-bond-update", "Scheduled daily bond update (snapshot + rate history)");
        try {
            bondDataService.updateBonds();
            taskTracker.completeTask("scheduled-bond-update", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-bond-update", started, e.getMessage());
            log.error("Daily bond update failed", e);
        }
    }
}
