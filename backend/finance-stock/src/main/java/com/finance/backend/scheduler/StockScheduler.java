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

    @Scheduled(cron = "0 5 18 * * MON-FRI", zone = "${app.timezone}")
    public void runDailyStockUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-stock-full", "Scheduled stock update (snapshots → candles)");
        try {
            stockDataService.updateStockSnapshots();
            stockDataService.updateStockCandles();
            taskTracker.completeTask("scheduled-stock-full", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-stock-full", started, e.getMessage());
            log.error("Daily stock update failed", e);
        }
    }
}