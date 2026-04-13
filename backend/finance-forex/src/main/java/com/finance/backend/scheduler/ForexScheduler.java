package com.finance.backend.scheduler;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.MarketUpdatePort;
import com.finance.backend.service.PortfolioSnapshotPort;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.ForexDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Log4j2
@Component
@RequiredArgsConstructor
public class ForexScheduler {
    private final TcmbForexService tcmbForexService;
    private final ForexDataService yahooForexService;
    private final TaskTrackingService taskTracker;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    private final Optional<MarketUpdatePort> marketUpdatePort;

    @Scheduled(cron = "${app.scheduler.forex.morning-cron}", zone = "${app.timezone}")
    public void runMorningForexUpdate() {
        executeForexUpdate("scheduled-forex-morning", "Scheduled morning forex update (10:30)");
    }

    @Scheduled(cron = "${app.scheduler.forex.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonForexUpdate() {
        executeForexUpdate("scheduled-forex-afternoon", "Scheduled afternoon forex update (16:00)");
    }

    @Scheduled(cron = "${app.scheduler.forex.evening-cron}", zone = "${app.timezone}")
    public void runEveningForexUpdate() {
        executeForexUpdate("scheduled-forex-evening", "Scheduled evening forex update (22:00)");
    }

    private void executeForexUpdate(String taskType, String description) {
        TaskInfo started = taskTracker.startTask(taskType, description);
        try {
            tcmbForexService.fetchAndSaveTcmbRates();
            yahooForexService.syncAllYahooSnapshots();
            yahooForexService.syncAllYahooCandles();
            portfolioSnapshotPort.ifPresent(port -> port.onMarketUpdate(MarketType.FOREX));
            marketUpdatePort.ifPresent(port -> port.onMarketDataUpdated(MarketType.FOREX));
            taskTracker.completeTask(taskType, started);
        } catch (Exception e) {
            taskTracker.failTask(taskType, started, e.getMessage());
            log.error("{} failed", taskType, e);
        }
    }
}
