package com.finance.backend.scheduler;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.MarketUpdatePort;
import com.finance.backend.service.PortfolioSnapshotPort;
import com.finance.backend.service.StockDataService;
import com.finance.backend.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Log4j2
@Component
@RequiredArgsConstructor
public class StockScheduler {
    private final StockDataService stockDataService;
    private final TaskTrackingService taskTracker;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    private final Optional<MarketUpdatePort> marketUpdatePort;

    @Scheduled(cron = "${app.scheduler.stock.morning-cron}", zone = "${app.timezone}")
    public void runMorningStockUpdate() {
        executeStockUpdate("scheduled-stock-morning", "Scheduled morning stock update (10:15)");
    }

    @Scheduled(cron = "${app.scheduler.stock.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonStockUpdate() {
        executeStockUpdate("scheduled-stock-afternoon", "Scheduled afternoon stock update (14:15)");
    }

    @Scheduled(cron = "${app.scheduler.stock.evening-cron}", zone = "${app.timezone}")
    public void runEveningStockUpdate() {
        executeStockUpdate("scheduled-stock-evening", "Scheduled evening stock update (19:15)");
    }

    private void executeStockUpdate(String taskType, String description) {
        taskTracker.runTracked(taskType, description, () -> {
            stockDataService.updateStockSnapshots();
            stockDataService.updateStockCandles();
            portfolioSnapshotPort.ifPresent(port -> port.onMarketUpdate(MarketType.STOCK));
            marketUpdatePort.ifPresent(port -> port.onMarketDataUpdated(MarketType.STOCK));
        });
    }
}
