package com.finance.market.bond.scheduler;

import com.finance.market.bond.service.BondDataService;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.shared.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily bond refresh (snapshot + rate history), publishing a market-updated event on completion. */
@Log4j2
@Component
@RequiredArgsConstructor
public class BondScheduler {

    private static final String DAILY_TASK_TYPE = "scheduled-bond-daily";

    private final BondDataService bondDataService;
    private final TaskTrackingService taskTracker;
    private final ObjectProvider<EventPublisherPort> events;

    @Scheduled(cron = "${app.scheduler.bond.daily-cron}", zone = "${app.timezone}")
    public void runDailyBondUpdate() {
        taskTracker.runTracked(DAILY_TASK_TYPE,
                "Scheduled daily bond update (snapshot + rate history)",
                () -> {
                    bondDataService.updateBonds();
                    events.ifAvailable(port -> port.publish(MarketUpdatedEvent.of(MarketType.BOND, DAILY_TASK_TYPE)));
                });
    }
}
