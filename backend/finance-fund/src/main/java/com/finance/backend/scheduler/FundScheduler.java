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

    @Scheduled(cron = "0 30 11 * * *", zone = "Europe/Istanbul")
    public void runDailyFundSnapshot() {
        TaskInfo started = taskTracker.startTask("scheduled-fund-snapshot", "Scheduled fund snapshot update");
        try {
            fundDataService.updateFundSnapshots();
            taskTracker.completeTask("scheduled-fund-snapshot", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-fund-snapshot", started, e.getMessage());
            log.error("Fund snapshot update failed", e);
        }
    }

    @Scheduled(cron = "0 45 11 * * *", zone = "Europe/Istanbul")
    public void runDailyFundCandleUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-fund-candles", "Scheduled fund candle sync");
        try {
            fundDataService.updateFundCandles();
            taskTracker.completeTask("scheduled-fund-candles", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-fund-candles", started, e.getMessage());
            log.error("Fund candle sync failed", e);
        }
    }
}
