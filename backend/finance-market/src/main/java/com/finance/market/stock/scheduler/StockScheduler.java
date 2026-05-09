package com.finance.market.stock.scheduler;
import com.finance.market.core.scheduler.AbstractMarketScheduler;

import com.finance.market.core.scheduler.SchedulerPorts;


import com.finance.common.model.MarketType;
import com.finance.market.stock.service.StockDataService;
import com.finance.shared.service.TaskTrackingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class StockScheduler extends AbstractMarketScheduler {

    private final StockDataService stockDataService;

    public StockScheduler(StockDataService stockDataService,
                          TaskTrackingService taskTracker,
                          SchedulerPorts ports) {
        super(taskTracker, ports);
        this.stockDataService = stockDataService;
    }

    @Override
    protected MarketType marketType() {
        return MarketType.STOCK;
    }

    @Override
    protected void runRefresh() {
        stockDataService.refreshAll();
    }

    @Scheduled(cron = "${app.scheduler.stock.morning-cron}", zone = "${app.timezone}")
    public void runMorningStockUpdate() {
        executeMarketUpdate("scheduled-stock-morning", "Scheduled morning stock update (10:15)");
    }

    @Scheduled(cron = "${app.scheduler.stock.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonStockUpdate() {
        executeMarketUpdate("scheduled-stock-afternoon", "Scheduled afternoon stock update (14:15)");
    }

    @Scheduled(cron = "${app.scheduler.stock.evening-cron}", zone = "${app.timezone}")
    public void runEveningStockUpdate() {
        executeMarketUpdate("scheduled-stock-evening", "Scheduled evening stock update (19:15)");
    }
}
