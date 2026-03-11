package com.finance.backend.scheduler;

import com.finance.backend.service.FundDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class FundScheduler {

    private final FundDataService fundDataService;
    private final TaskTrackingService taskTracker;

    @Scheduled(cron = "0 30 11 * * *", zone = "${app.timezone}")
    public void runDailyFundUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-fund-full", "Scheduled fund update (snapshots → candles)");
        try {
            fundDataService.updateFundSnapshots();
            fundDataService.updateFundCandles();
            taskTracker.completeTask("scheduled-fund-full", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-fund-full", started, e.getMessage());
            log.error("Daily fund update failed", e);
        }
    }
}
