package com.finance.backend.scheduler;

import com.finance.backend.model.MarketType;
import com.finance.backend.service.CommodityDataService;
import com.finance.backend.service.MarketUpdatePort;
import com.finance.backend.service.PortfolioSnapshotPort;
import com.finance.backend.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CommodityScheduler {

    private final CommodityDataService commodityDataService;
    private final TaskTrackingService taskTracker;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    private final Optional<MarketUpdatePort> marketUpdatePort;

    @Scheduled(cron = "${app.scheduler.commodity.morning-cron}", zone = "${app.timezone}")
    public void runMorningCommodityUpdate() {
        executeCommodityUpdate("scheduled-commodity-morning", "Scheduled morning commodity update");
    }

    @Scheduled(cron = "${app.scheduler.commodity.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonCommodityUpdate() {
        executeCommodityUpdate("scheduled-commodity-afternoon", "Scheduled afternoon commodity update");
    }

    @Scheduled(cron = "${app.scheduler.commodity.evening-cron}", zone = "${app.timezone}")
    public void runEveningCommodityUpdate() {
        executeCommodityUpdate("scheduled-commodity-evening", "Scheduled evening commodity update");
    }

    private void executeCommodityUpdate(String taskType, String description) {
        taskTracker.runTracked(taskType, description, () -> {
            commodityDataService.updateCommoditySnapshots();
            commodityDataService.updateCommodityCandles();
            portfolioSnapshotPort.ifPresent(port -> port.onMarketUpdate(MarketType.COMMODITY));
            marketUpdatePort.ifPresent(port -> port.onMarketDataUpdated(MarketType.COMMODITY));
        });
    }
}
