package com.finance.market.core.scheduler;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.shared.service.TaskTrackingService;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class AbstractMarketScheduler {

    protected final TaskTrackingService taskTracker;
    protected final SchedulerPorts ports;

    protected AbstractMarketScheduler(TaskTrackingService taskTracker, SchedulerPorts ports) {
        this.taskTracker = taskTracker;
        this.ports = ports;
    }

    protected abstract MarketType marketType();

    protected abstract void runRefresh();

    protected final void executeMarketUpdate(String taskType, String description) {
        taskTracker.runTracked(taskType, description, () -> {
            runRefresh();
            runPostRefreshHooks(taskType);
        });
    }

    private void runPostRefreshHooks(String taskType) {
        safely("portfolio snapshot", () -> ports.portfolio().ifPresent(port -> port.onMarketUpdate(marketType())));
        safely("market cache refresh", () -> ports.market().ifPresent(port -> port.onMarketDataUpdated(marketType())));
        safely("market event publish",
                () -> ports.events().ifPresent(port -> port.publish(MarketUpdatedEvent.of(marketType(), taskType))));
    }

    private void safely(String hook, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("{} hook failed for {} after a successful refresh: {}",
                    hook, marketType(), e.getMessage(), e);
        }
    }
}
