package com.finance.market.forex.scheduler;
import com.finance.market.core.scheduler.AbstractMarketScheduler;
import com.finance.market.core.scheduler.SchedulerPorts;

import com.finance.common.model.MarketType;
import com.finance.market.forex.service.ForexDataService;
import com.finance.shared.service.TaskTrackingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Triggers the daily forex refresh on a configurable cron/timezone (after the EVDS publish time). */
@Log4j2
@Component
public class ForexScheduler extends AbstractMarketScheduler {

    private final ForexDataService forexDataService;

    public ForexScheduler(ForexDataService forexDataService,
                          TaskTrackingService taskTracker,
                          SchedulerPorts ports) {
        super(taskTracker, ports);
        this.forexDataService = forexDataService;
    }

    @Override
    protected MarketType marketType() {
        return MarketType.FOREX;
    }

    @Override
    protected void runRefresh() {
        forexDataService.refreshAll();
    }

    @Scheduled(cron = "${app.scheduler.forex.daily-cron}", zone = "${app.timezone}")
    public void runDailyForexUpdate() {
        executeMarketUpdate("scheduled-forex-daily", "Scheduled daily forex update (16:30)");
    }
}
