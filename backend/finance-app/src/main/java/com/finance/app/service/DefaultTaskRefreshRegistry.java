package com.finance.app.service;

import com.finance.common.event.MacroIndicatorsUpdatedEvent;
import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.market.bond.service.BondDataService;
import com.finance.market.core.service.MarketRefresher;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.market.macro.service.MacroIndicatorFetchService;
import com.finance.market.macro.service.MacroIndicatorRegistryService;
import com.finance.news.service.article.NewsDataService;
import com.finance.shared.event.EventPublisherPort;
import com.finance.shared.service.PortfolioSnapshotPort;
import com.finance.shared.util.EnumDispatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link TaskRefreshRegistry}: runs the per-type market refresher, then notifies the portfolio
 * and market-cache ports and publishes a market/macro updated event. Downstream notification failures are
 * logged and swallowed so the refresh itself still counts as done.
 */
@Log4j2
@Component
class DefaultTaskRefreshRegistry implements TaskRefreshRegistry {

    private final Map<MarketType, MarketRefresher> refreshers;
    private final BondDataService bondDataService;
    private final NewsDataService newsDataService;
    private final MacroIndicatorRegistryService macroRegistry;
    private final MacroIndicatorFetchService macroFetcher;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    private final Optional<MarketUpdatePort> marketUpdatePort;
    private final Optional<EventPublisherPort> eventPublisher;

    DefaultTaskRefreshRegistry(List<MarketRefresher> refreshers,
                               BondDataService bondDataService,
                               NewsDataService newsDataService,
                               MacroIndicatorRegistryService macroRegistry,
                               MacroIndicatorFetchService macroFetcher,
                               Optional<PortfolioSnapshotPort> portfolioSnapshotPort,
                               Optional<MarketUpdatePort> marketUpdatePort,
                               Optional<EventPublisherPort> eventPublisher) {
        this.refreshers = EnumDispatcher.from(MarketType.class, refreshers, MarketRefresher::getMarketType);
        this.bondDataService = bondDataService;
        this.newsDataService = newsDataService;
        this.macroRegistry = macroRegistry;
        this.macroFetcher = macroFetcher;
        this.portfolioSnapshotPort = portfolioSnapshotPort;
        this.marketUpdatePort = marketUpdatePort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void runMarketRefresh(MarketType type) {
        resolveRefresher(type).refreshAll();
        triggerPortfolioSnapshot(type);
        publishMarketEvent(type);
    }

    @Override
    public void runBondUpdate() {
        bondDataService.updateBonds();
    }

    @Override
    public void runNewsUpdate() {
        newsDataService.updateNews();
    }

    @Override
    public void runMacroRefresh() {
        macroRegistry.synchronizeFromConfig();
        MacroIndicatorFetchService.FetchOutcome outcome = macroFetcher.refreshAll();
        if (!outcome.changedCodes().isEmpty()) {
            publishMacroEvent(outcome.changedCodes());
        }
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

    private void publishMarketEvent(MarketType type) {
        eventPublisher.ifPresent(port -> {
            try {
                port.publish(MarketUpdatedEvent.of(type, "admin"));
            } catch (Exception e) {
                log.warn("Market event publish failed for {}: {}", type, e.getMessage());
            }
        });
    }

    private void publishMacroEvent(List<String> changedCodes) {
        eventPublisher.ifPresent(port -> {
            try {
                port.publish(MacroIndicatorsUpdatedEvent.of("admin", changedCodes));
            } catch (Exception e) {
                log.warn("Macro indicators event publish failed: {}", e.getMessage());
            }
        });
    }
}
