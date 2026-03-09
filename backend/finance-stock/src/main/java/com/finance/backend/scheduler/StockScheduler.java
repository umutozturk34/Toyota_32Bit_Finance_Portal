package com.finance.backend.scheduler;
import com.finance.backend.service.StockDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class StockScheduler {
    private final StockDataService stockDataService;
    private final TaskTrackingService taskTracker;
    @Scheduled(cron = "0 5 18 * * MON-FRI", zone = "Europe/Istanbul")
    public void runDailyStockSnapshot() {
        log.info("[STOCK-SCHEDULER] Running end-of-day stock snapshot update...");
        TaskInfo started = taskTracker.startTask("scheduled-stock-snapshot", "Scheduled stock snapshot update");
        try {
            stockDataService.updateStockSnapshots();
            taskTracker.completeTask("scheduled-stock-snapshot", started);
            log.info("[STOCK-SCHEDULER] Stock snapshots updated successfully.");
        } catch (Exception e) {
            taskTracker.failTask("scheduled-stock-snapshot", started, e.getMessage());
            log.error("[STOCK-SCHEDULER-ERROR] Failed to update snapshots: {}", e.getMessage());
        }
    }
    @Scheduled(cron = "0 30 18 * * MON-FRI", zone = "Europe/Istanbul")
    public void runHeavyDutyCandleUpdate() {
        log.info("[STOCK-SCHEDULER] Starting heavy-duty 5-year candle sync...");
        TaskInfo started = taskTracker.startTask("scheduled-stock-candles", "Scheduled stock candle sync (5y)");
        try {
            stockDataService.updateStockCandles();
            taskTracker.completeTask("scheduled-stock-candles", started);
            log.info("[STOCK-SCHEDULER] Candle sync completed.");
        } catch (Exception e) {
            taskTracker.failTask("scheduled-stock-candles", started, e.getMessage());
            log.error("[STOCK-SCHEDULER-ERROR] Candle sync failed: {}", e.getMessage());
        }
    }
}