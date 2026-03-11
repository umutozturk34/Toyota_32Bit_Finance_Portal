package com.finance.backend.scheduler;
import com.finance.backend.service.StockDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Log4j2
@Component
@RequiredArgsConstructor
public class StockScheduler {
    private final StockDataService stockDataService;
    private final TaskTrackingService taskTracker;
    @Scheduled(cron = "0 5 18 * * MON-FRI", zone = "Europe/Istanbul")
    public void runDailyStockSnapshot() {
        TaskInfo started = taskTracker.startTask("scheduled-stock-snapshot", "Scheduled stock snapshot update");
        try {
            stockDataService.updateStockSnapshots();
            taskTracker.completeTask("scheduled-stock-snapshot", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-stock-snapshot", started, e.getMessage());
            log.error("Stock snapshot update failed", e);
        }
    }
    @Scheduled(cron = "0 30 18 * * MON-FRI", zone = "Europe/Istanbul")
    public void runHeavyDutyCandleUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-stock-candles", "Scheduled stock candle sync (5y)");
        try {
            stockDataService.updateStockCandles();
            taskTracker.completeTask("scheduled-stock-candles", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-stock-candles", started, e.getMessage());
            log.error("Stock candle sync failed", e);
        }
    }
}