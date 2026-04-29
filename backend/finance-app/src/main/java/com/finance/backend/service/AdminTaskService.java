package com.finance.backend.service;

import com.finance.backend.dto.response.TaskStatusResponse;
import com.finance.backend.dto.response.TaskTriggerResponse;
import com.finance.backend.model.MarketType;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@Log4j2
@Service
public class AdminTaskService {

    private final Map<MarketType, MarketRefresher> refreshers;
    private final TcmbForexService tcmbForexService;
    private final BondDataService bondDataService;
    private final NewsDataService newsDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    private final Optional<MarketUpdatePort> marketUpdatePort;

    public AdminTaskService(List<MarketRefresher> refreshers,
                            TcmbForexService tcmbForexService,
                            BondDataService bondDataService,
                            NewsDataService newsDataService,
                            TaskTrackingService taskTracker,
                            Executor taskExecutor,
                            Optional<PortfolioSnapshotPort> portfolioSnapshotPort,
                            Optional<MarketUpdatePort> marketUpdatePort) {
        this.refreshers = new EnumMap<>(MarketType.class);
        refreshers.forEach(r -> this.refreshers.put(r.getMarketType(), r));
        this.tcmbForexService = tcmbForexService;
        this.bondDataService = bondDataService;
        this.newsDataService = newsDataService;
        this.taskTracker = taskTracker;
        this.taskExecutor = taskExecutor;
        this.portfolioSnapshotPort = portfolioSnapshotPort;
        this.marketUpdatePort = marketUpdatePort;
    }

    public TaskTriggerResponse triggerSnapshot(MarketType type) {
        return triggerMarketRefresh(type, "snapshot", " snapshot update started in background");
    }

    public TaskTriggerResponse triggerCandles(MarketType type) {
        return triggerMarketRefresh(type, "candles", " candle update started in background");
    }

    public TaskTriggerResponse triggerFull(MarketType type) {
        return triggerMarketRefresh(type, "full", " full market update started in background");
    }

    public TaskTriggerResponse triggerBondUpdate() {
        return executeTask("bond-update",
                "Bond update started in background",
                bondDataService::updateBonds);
    }

    public TaskTriggerResponse triggerNewsUpdate() {
        return executeTask("news-update",
                "News feed update started in background",
                newsDataService::updateNews);
    }

    public TaskStatusResponse getTaskStatus() {
        return taskTracker.getTypedStatus();
    }

    private TaskTriggerResponse triggerMarketRefresh(MarketType type, String suffix, String messageTail) {
        String taskType = type.name().toLowerCase() + "-" + suffix;
        String message = type.name() + messageTail;
        return executeTask(taskType, message, () -> {
            runForexPreStep(type);
            resolveRefresher(type).refreshAll();
            triggerPortfolioSnapshot(type);
        });
    }

    private MarketRefresher resolveRefresher(MarketType type) {
        MarketRefresher refresher = refreshers.get(type);
        if (refresher == null) {
            throw new IllegalArgumentException("No refresher registered for " + type);
        }
        return refresher;
    }

    private void runForexPreStep(MarketType type) {
        if (type == MarketType.FOREX) {
            tcmbForexService.fetchAndSaveTcmbRates();
        }
    }

    private void triggerPortfolioSnapshot(MarketType assetType) {
        portfolioSnapshotPort.ifPresent(port -> {
            try {
                port.onMarketUpdate(assetType);
            } catch (Exception e) {
                log.warn("Portfolio snapshot failed for {}: {}", assetType, e.getMessage());
            }
        });
        marketUpdatePort.ifPresent(port -> {
            try {
                port.onMarketDataUpdated(assetType);
            } catch (Exception e) {
                log.warn("Market cache update failed for {}: {}", assetType, e.getMessage());
            }
        });
    }

    private TaskTriggerResponse executeTask(String taskType, String message, Runnable task) {
        TaskTrackingService.TaskInfo info = taskTracker.startTask(taskType, message);
        log.info("Task started: {}", taskType);

        taskExecutor.execute(() -> {
            try {
                task.run();
                taskTracker.completeTask(taskType, info);
                log.info("Task completed: {}", taskType);
            } catch (Exception e) {
                taskTracker.failTask(taskType, info, e.getMessage());
                log.error("Task failed: {}", taskType, e);
            }
        });

        return TaskTriggerResponse.started(taskType, message);
    }
}
