package com.finance.app.service;

import com.finance.shared.service.TaskTrackingService;

import com.finance.news.service.article.NewsDataService;

import com.finance.market.core.service.MarketUpdatePort;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.event.MarketUpdatedEvent;

import com.finance.market.bond.service.BondDataService;


import com.finance.shared.service.PortfolioSnapshotPort;


import com.finance.market.core.service.MarketRefresher;


import com.finance.shared.dto.response.TaskTriggerResponse;
import com.finance.common.model.MarketType;
import com.finance.shared.util.EnumDispatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@Log4j2
@Service
public class AdminTaskService {

    private final Map<MarketType, MarketRefresher> refreshers;
    private final BondDataService bondDataService;
    private final NewsDataService newsDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    private final Optional<MarketUpdatePort> marketUpdatePort;
    private final Optional<EventPublisherPort> eventPublisher;

    public AdminTaskService(List<MarketRefresher> refreshers,
                            BondDataService bondDataService,
                            NewsDataService newsDataService,
                            TaskTrackingService taskTracker,
                            Executor taskExecutor,
                            Optional<PortfolioSnapshotPort> portfolioSnapshotPort,
                            Optional<MarketUpdatePort> marketUpdatePort,
                            Optional<EventPublisherPort> eventPublisher) {
        this.refreshers = EnumDispatcher.from(MarketType.class, refreshers, MarketRefresher::getMarketType);
        this.bondDataService = bondDataService;
        this.newsDataService = newsDataService;
        this.taskTracker = taskTracker;
        this.taskExecutor = taskExecutor;
        this.portfolioSnapshotPort = portfolioSnapshotPort;
        this.marketUpdatePort = marketUpdatePort;
        this.eventPublisher = eventPublisher;
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

    private TaskTriggerResponse triggerMarketRefresh(MarketType type, String suffix, String messageTail) {
        String taskType = type.name().toLowerCase() + "-" + suffix;
        String message = type.name() + messageTail;
        return executeTask(taskType, message, () -> {
            resolveRefresher(type).refreshAll();
            triggerPortfolioSnapshot(type);
            publishMarketEvent(type);
        });
    }

    private void publishMarketEvent(MarketType type) {
        eventPublisher.ifPresent(port -> {
            try {
                port.publish(MarketUpdatedEvent.of(type, "admin"));
            } catch (Exception e) {
                log.warn("Market event publish failed for {}: {}", type, e.getMessage());
            }
        });
    }

    private MarketRefresher resolveRefresher(MarketType type) {
        MarketRefresher refresher = refreshers.get(type);
        if (refresher == null) {
            throw new IllegalArgumentException("error.admin.noRefresher");
        }
        return refresher;
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
