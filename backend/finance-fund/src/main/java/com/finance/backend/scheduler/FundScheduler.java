package com.finance.backend.scheduler;

import com.finance.backend.model.MarketType;
import com.finance.backend.service.FundDataService;
import com.finance.backend.service.TaskTrackingService;
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
        fundDataService.refreshAllSnapshots();
        fundDataService.refreshAllCandles();
    }

    @Scheduled(cron = "${app.scheduler.fund.daily-cron}", zone = "${app.timezone}")
    public void runDailyFundUpdate() {
        executeMarketUpdate("scheduled-fund-full", "Scheduled fund update (snapshots → candles)");
    }
}
