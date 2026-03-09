package com.finance.backend.scheduler;

import com.finance.backend.service.FundDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundScheduler {

    private final FundDataService fundDataService;
    private final TaskTrackingService taskTracker;

    @Scheduled(cron = "0 30 11 * * *", zone = "Europe/Istanbul")
    public void runDailyFundSnapshot() {
        log.info("[FUND-SCHEDULER] Running daily fund snapshot update...");
        TaskInfo started = taskTracker.startTask("scheduled-fund-snapshot", "Scheduled fund snapshot update");
        try {
            fundDataService.updateFundSnapshots();
            taskTracker.completeTask("scheduled-fund-snapshot", started);
            log.info("[FUND-SCHEDULER] Fund snapshots updated successfully.");
        } catch (Exception e) {
            taskTracker.failTask("scheduled-fund-snapshot", started, e.getMessage());
            log.error("[FUND-SCHEDULER-ERROR] Failed to update snapshots: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 45 11 * * *", zone = "Europe/Istanbul")
    public void runDailyFundCandleUpdate() {
        log.info("[FUND-SCHEDULER] Starting fund candle sync...");
        TaskInfo started = taskTracker.startTask("scheduled-fund-candles", "Scheduled fund candle sync");
        try {
            fundDataService.updateFundCandles();
            taskTracker.completeTask("scheduled-fund-candles", started);
            log.info("[FUND-SCHEDULER] Fund candle sync completed.");
        } catch (Exception e) {
            taskTracker.failTask("scheduled-fund-candles", started, e.getMessage());
            log.error("[FUND-SCHEDULER-ERROR] Fund candle sync failed: {}", e.getMessage());
        }
    }
}
