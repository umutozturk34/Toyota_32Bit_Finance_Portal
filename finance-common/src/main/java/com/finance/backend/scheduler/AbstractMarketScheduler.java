package com.finance.backend.scheduler;

import com.finance.backend.model.MarketType;
import com.finance.backend.service.TaskTrackingService;

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
            ports.portfolio().ifPresent(port -> port.onMarketUpdate(marketType()));
            ports.market().ifPresent(port -> port.onMarketDataUpdated(marketType()));
            ports.events().ifPresent(port -> port.publishMarketUpdated(marketType(), "scheduler"));
        });
    }
}
