package com.finance.backend.scheduler;

import com.finance.backend.service.FundDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundScheduler {

    private final FundDataService fundDataService;

    @Scheduled(cron = "0 30 11 * * *", zone = "Europe/Istanbul")
    public void runDailyFundSnapshot() {
        log.info("[FUND-SCHEDULER] Running daily fund snapshot update...");
        try {
            fundDataService.updateFundSnapshots();
            log.info("[FUND-SCHEDULER] Fund snapshots updated successfully.");
        } catch (Exception e) {
            log.error("[FUND-SCHEDULER-ERROR] Failed to update snapshots: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 45 11 * * *", zone = "Europe/Istanbul")
    public void runDailyFundCandleUpdate() {
        log.info("[FUND-SCHEDULER] Starting fund candle sync...");
        try {
            fundDataService.updateFundCandles();
            log.info("[FUND-SCHEDULER] Fund candle sync completed.");
        } catch (Exception e) {
            log.error("[FUND-SCHEDULER-ERROR] Fund candle sync failed: {}", e.getMessage());
        }
    }
}
