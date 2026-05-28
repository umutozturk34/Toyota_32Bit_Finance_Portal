package com.finance.app.service;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.MarketRefresher;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.shared.event.EventPublisherPort;
import com.finance.shared.service.PortfolioSnapshotPort;
import com.finance.shared.util.EnumDispatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a single market type's refresh and fans out the follow-ups: portfolio snapshot, market-cache
 * write-through, and a market-updated event. Each follow-up is best-effort, with failures logged and
 * isolated so one broken downstream doesn't abort the others.
 */
@Log4j2
@Component
class MarketRefreshOrchestrator {

    private static final String EVENT_SOURCE = "admin";

    private final Map<MarketType, MarketRefresher> refreshers;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    private final Optional<MarketUpdatePort> marketUpdatePort;
    private final Optional<EventPublisherPort> eventPublisher;

    MarketRefreshOrchestrator(List<MarketRefresher> refreshers,
                              Optional<PortfolioSnapshotPort> portfolioSnapshotPort,
                              Optional<MarketUpdatePort> marketUpdatePort,
                              Optional<EventPublisherPort> eventPublisher) {
        this.refreshers = EnumDispatcher.from(MarketType.class, refreshers, MarketRefresher::getMarketType);
        this.portfolioSnapshotPort = portfolioSnapshotPort;
        this.marketUpdatePort = marketUpdatePort;
        this.eventPublisher = eventPublisher;
    }

    void refresh(MarketType type) {
        resolveRefresher(type).refreshAll();
        notifyPortfolio(type);
        notifyMarketCache(type);
        publishMarketEvent(type);
    }

    private MarketRefresher resolveRefresher(MarketType type) {
        MarketRefresher refresher = refreshers.get(type);
        if (refresher == null) {
            throw new IllegalArgumentException("error.admin.noRefresher");
        }
        return refresher;
    }

    private void notifyPortfolio(MarketType assetType) {
        portfolioSnapshotPort.ifPresent(port -> {
            try {
                port.onMarketUpdate(assetType);
            } catch (Exception e) {
                log.warn("Portfolio snapshot failed for {}: {}", assetType, e.getMessage());
            }
        });
    }

    private void notifyMarketCache(MarketType assetType) {
        marketUpdatePort.ifPresent(port -> {
            try {
                port.onMarketDataUpdated(assetType);
            } catch (Exception e) {
                log.warn("Market cache update failed for {}: {}", assetType, e.getMessage());
            }
        });
    }

    private void publishMarketEvent(MarketType type) {
        eventPublisher.ifPresent(port -> {
            try {
                port.publish(MarketUpdatedEvent.of(type, EVENT_SOURCE));
            } catch (Exception e) {
                log.warn("Market event publish failed for {}: {}", type, e.getMessage());
            }
        });
    }
}
