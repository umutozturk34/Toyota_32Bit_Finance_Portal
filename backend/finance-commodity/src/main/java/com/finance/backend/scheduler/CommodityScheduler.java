package com.finance.backend.scheduler;

import com.finance.backend.model.MarketType;
import com.finance.backend.service.CommodityDataService;
import com.finance.backend.service.TaskTrackingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommodityScheduler extends AbstractMarketScheduler {

    private final CommodityDataService commodityDataService;

    public CommodityScheduler(CommodityDataService commodityDataService,
                              TaskTrackingService taskTracker,
                              SchedulerPorts ports) {
        super(taskTracker, ports);
        this.commodityDataService = commodityDataService;
    }

    @Override
    protected MarketType marketType() {
        return MarketType.COMMODITY;
    }

    @Override
    protected void runRefresh() {
        commodityDataService.refreshAll();
    }

    @Scheduled(cron = "${app.scheduler.commodity.morning-cron}", zone = "${app.timezone}")
    public void runMorningCommodityUpdate() {
        executeMarketUpdate("scheduled-commodity-morning", "Scheduled morning commodity update");
    }

    @Scheduled(cron = "${app.scheduler.commodity.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonCommodityUpdate() {
        executeMarketUpdate("scheduled-commodity-afternoon", "Scheduled afternoon commodity update");
    }

    @Scheduled(cron = "${app.scheduler.commodity.evening-cron}", zone = "${app.timezone}")
    public void runEveningCommodityUpdate() {
        executeMarketUpdate("scheduled-commodity-evening", "Scheduled evening commodity update");
    }
}
