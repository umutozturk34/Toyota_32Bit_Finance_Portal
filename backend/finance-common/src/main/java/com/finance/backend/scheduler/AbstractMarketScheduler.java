package com.finance.backend.scheduler;

import com.finance.backend.model.MarketType;
import com.finance.backend.service.MarketUpdatePort;
import com.finance.backend.service.PortfolioSnapshotPort;
import com.finance.backend.service.TaskTrackingService;

import java.util.Optional;

public abstract class AbstractMarketScheduler {

    protected final TaskTrackingService taskTracker;
    protected final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    protected final Optional<MarketUpdatePort> marketUpdatePort;

    protected AbstractMarketScheduler(TaskTrackingService taskTracker,
                                      Optional<PortfolioSnapshotPort> portfolioSnapshotPort,
                                      Optional<MarketUpdatePort> marketUpdatePort) {
        this.taskTracker = taskTracker;
        this.portfolioSnapshotPort = portfolioSnapshotPort;
        this.marketUpdatePort = marketUpdatePort;
    }

    protected abstract MarketType marketType();

    protected abstract void runRefresh();

    protected final void executeMarketUpdate(String taskType, String description) {
        taskTracker.runTracked(taskType, description, () -> {
            runRefresh();
            portfolioSnapshotPort.ifPresent(port -> port.onMarketUpdate(marketType()));
            marketUpdatePort.ifPresent(port -> port.onMarketDataUpdated(marketType()));
        });
    }
}
