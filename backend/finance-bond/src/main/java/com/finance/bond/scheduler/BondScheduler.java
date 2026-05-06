package com.finance.bond.scheduler;

import com.finance.bond.service.BondDataService;
import com.finance.common.event.MarketUpdateEventPort;
import com.finance.common.model.MarketType;
import com.finance.common.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class BondScheduler {

    private static final String SCHEDULED_SOURCE = "scheduler";

    private final BondDataService bondDataService;
    private final TaskTrackingService taskTracker;
    private final ObjectProvider<MarketUpdateEventPort> marketEvents;

    @Scheduled(cron = "${app.scheduler.bond.daily-cron}", zone = "${app.timezone}")
    public void runDailyBondUpdate() {
        taskTracker.runTracked("scheduled-bond-update",
                "Scheduled daily bond update (snapshot + rate history)",
                () -> {
                    bondDataService.updateBonds();
                    marketEvents.ifAvailable(port -> port.publishMarketUpdated(MarketType.BOND, SCHEDULED_SOURCE));
                });
    }
}
