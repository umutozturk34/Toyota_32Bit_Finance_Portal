package com.finance.market.crypto.scheduler;
import com.finance.market.core.scheduler.AbstractMarketScheduler;

import com.finance.market.core.scheduler.SchedulerPorts;


import com.finance.common.model.MarketType;
import com.finance.market.crypto.service.CryptoDataService;
import com.finance.shared.service.TaskTrackingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Triggers crypto refreshes three times daily (morning/afternoon/evening) on configurable crons. */
@Log4j2
@Component
public class CryptoScheduler extends AbstractMarketScheduler {

    private final CryptoDataService marketDataService;

    public CryptoScheduler(CryptoDataService marketDataService,
                           TaskTrackingService taskTracker,
                           SchedulerPorts ports) {
        super(taskTracker, ports);
        this.marketDataService = marketDataService;
    }

    @Override
    protected MarketType marketType() {
        return MarketType.CRYPTO;
    }

    @Override
    protected void runRefresh() {
        marketDataService.refreshAll();
    }

    @Scheduled(cron = "${app.scheduler.crypto.morning-cron}", zone = "${app.timezone}")
    public void runMorningCryptoUpdate() {
        executeMarketUpdate("scheduled-crypto-morning", "Scheduled morning crypto update (09:00)");
    }

    @Scheduled(cron = "${app.scheduler.crypto.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonCryptoUpdate() {
        executeMarketUpdate("scheduled-crypto-afternoon", "Scheduled afternoon crypto update (15:00)");
    }

    @Scheduled(cron = "${app.scheduler.crypto.evening-cron}", zone = "${app.timezone}")
    public void runEveningCryptoUpdate() {
        executeMarketUpdate("scheduled-crypto-evening", "Scheduled evening crypto update (21:00)");
    }
}
