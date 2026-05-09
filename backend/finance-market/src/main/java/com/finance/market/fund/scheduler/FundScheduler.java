package com.finance.market.fund.scheduler;
import com.finance.market.core.scheduler.AbstractMarketScheduler;

import com.finance.market.core.scheduler.SchedulerPorts;


import com.finance.common.model.MarketType;
import com.finance.market.fund.service.FundDataService;
import com.finance.shared.service.TaskTrackingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class FundScheduler extends AbstractMarketScheduler {

    private final FundDataService fundDataService;

    public FundScheduler(FundDataService fundDataService,
                         TaskTrackingService taskTracker,
                         SchedulerPorts ports) {
        super(taskTracker, ports);
        this.fundDataService = fundDataService;
    }

    @Override
    protected MarketType marketType() {
        return MarketType.FUND;
    }

    @Override
    protected void runRefresh() {
        fundDataService.refreshAll();
    }

    @Scheduled(cron = "${app.scheduler.fund.daily-cron}", zone = "${app.timezone}")
    public void runDailyFundUpdate() {
        executeMarketUpdate("scheduled-fund-full", "Scheduled fund update (snapshots → candles)");
    }
}
