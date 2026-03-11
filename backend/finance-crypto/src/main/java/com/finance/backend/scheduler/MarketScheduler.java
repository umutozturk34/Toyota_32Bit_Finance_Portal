package com.finance.backend.scheduler;
import com.finance.backend.service.MarketDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Log4j2
@Component
@RequiredArgsConstructor
public class MarketScheduler {
    private final MarketDataService marketDataService;
    private final TaskTrackingService taskTracker;
    @Scheduled(cron = "0 30 19 * * *", zone = "Europe/Istanbul")
    public void runFullDailyMarketUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-crypto-full", "Scheduled daily crypto update (snapshots + candles)");
        try {
            marketDataService.updateOnlySnapshots();
            marketDataService.updateOnlyCandles();
            taskTracker.completeTask("scheduled-crypto-full", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-crypto-full", started, e.getMessage());
            log.error("Daily crypto update failed", e);
        }
    }
}