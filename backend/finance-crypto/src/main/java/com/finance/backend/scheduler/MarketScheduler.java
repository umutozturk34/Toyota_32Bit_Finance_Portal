package com.finance.backend.scheduler;
import com.finance.backend.service.MarketDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketScheduler {
    private final MarketDataService marketDataService;
    private final TaskTrackingService taskTracker;
    @Scheduled(cron = "0 30 19 * * *", zone = "Europe/Istanbul")
    public void runFullDailyMarketUpdate() {
        log.info("[MARKET-SCHEDULER] Starting full daily market synchronization...");
        TaskInfo started = taskTracker.startTask("scheduled-crypto-full", "Scheduled daily crypto update (snapshots + candles)");
        try {
            log.info("[MARKET-SCHEDULER] Step 1/2: Updating crypto snapshots...");
            marketDataService.updateOnlySnapshots();
            log.info("[MARKET-SCHEDULER] Step 2/2: Updating candle history and self-healing...");
            marketDataService.updateOnlyCandles();
            taskTracker.completeTask("scheduled-crypto-full", started);
            log.info("[MARKET-SCHEDULER] Full daily update completed successfully.");
        } catch (Exception e) {
            taskTracker.failTask("scheduled-crypto-full", started, e.getMessage());
            log.error("[MARKET-SCHEDULER-FATAL] Daily update failed! Error: {}", e.getMessage(), e);
        }
    }
}