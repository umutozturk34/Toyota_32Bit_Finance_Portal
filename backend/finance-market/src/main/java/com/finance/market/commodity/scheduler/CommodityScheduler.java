package com.finance.market.commodity.scheduler;
import com.finance.market.core.scheduler.AbstractMarketScheduler;

import com.finance.market.core.scheduler.SchedulerPorts;


import com.finance.common.model.MarketType;
import com.finance.market.commodity.service.CommodityDataService;
import com.finance.shared.service.TaskTrackingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Triggers commodity refreshes three times daily (morning/afternoon/evening) on configurable crons. */
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
        executeMarketUpdate("scheduled-commodity-morning", "Scheduled morning commodity update (10:45)");
    }

    @Scheduled(cron = "${app.scheduler.commodity.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonCommodityUpdate() {
        executeMarketUpdate("scheduled-commodity-afternoon", "Scheduled afternoon commodity update (16:30)");
    }

    @Scheduled(cron = "${app.scheduler.commodity.evening-cron}", zone = "${app.timezone}")
    public void runEveningCommodityUpdate() {
        executeMarketUpdate("scheduled-commodity-evening", "Scheduled evening commodity update (22:30)");
    }
}
