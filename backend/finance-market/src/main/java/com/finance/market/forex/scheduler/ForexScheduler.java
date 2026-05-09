package com.finance.market.forex.scheduler;
import com.finance.market.core.scheduler.AbstractMarketScheduler;

import com.finance.market.core.scheduler.SchedulerPorts;


import com.finance.common.model.MarketType;
import com.finance.market.forex.service.ForexDataService;
import com.finance.common.service.TaskTrackingService;
import com.finance.market.forex.service.TcmbForexService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class ForexScheduler extends AbstractMarketScheduler {

    private final TcmbForexService tcmbForexService;
    private final ForexDataService yahooForexService;

    public ForexScheduler(TcmbForexService tcmbForexService,
                          ForexDataService yahooForexService,
                          TaskTrackingService taskTracker,
                          SchedulerPorts ports) {
        super(taskTracker, ports);
        this.tcmbForexService = tcmbForexService;
        this.yahooForexService = yahooForexService;
    }

    @Override
    protected MarketType marketType() {
        return MarketType.FOREX;
    }

    @Override
    protected void runRefresh() {
        tcmbForexService.fetchAndSaveTcmbRates();
        yahooForexService.syncAllYahoo();
    }

    @Scheduled(cron = "${app.scheduler.forex.morning-cron}", zone = "${app.timezone}")
    public void runMorningForexUpdate() {
        executeMarketUpdate("scheduled-forex-morning", "Scheduled morning forex update (10:30)");
    }

    @Scheduled(cron = "${app.scheduler.forex.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonForexUpdate() {
        executeMarketUpdate("scheduled-forex-afternoon", "Scheduled afternoon forex update (16:00)");
    }

    @Scheduled(cron = "${app.scheduler.forex.evening-cron}", zone = "${app.timezone}")
    public void runEveningForexUpdate() {
        executeMarketUpdate("scheduled-forex-evening", "Scheduled evening forex update (22:00)");
    }
}
