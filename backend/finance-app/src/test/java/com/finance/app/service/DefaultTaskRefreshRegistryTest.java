package com.finance.app.service;

import com.finance.common.event.DomainEvent;
import com.finance.common.model.MarketType;
import com.finance.market.bond.service.BondDataService;
import com.finance.market.core.service.MarketRefresher;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.market.macro.service.MacroIndicatorFetchService;
import com.finance.market.macro.service.MacroIndicatorRegistryService;
import com.finance.news.service.article.NewsDataService;
import com.finance.shared.event.EventPublisherPort;
import com.finance.shared.service.PortfolioSnapshotPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTaskRefreshRegistryTest {

    @Mock private MarketRefresher stockRefresher;
    @Mock private BondDataService bondDataService;
    @Mock private NewsDataService newsDataService;
    @Mock private MacroIndicatorRegistryService macroRegistry;
    @Mock private MacroIndicatorFetchService macroFetcher;
    @Mock private PortfolioSnapshotPort portfolioSnapshotPort;
    @Mock private MarketUpdatePort marketUpdatePort;
    @Mock private EventPublisherPort eventPublisher;

    @BeforeEach
    void wireRefresherType() {
        lenient().when(stockRefresher.getMarketType()).thenReturn(MarketType.STOCK);
    }

    private DefaultTaskRefreshRegistry registryWithAllPorts() {
        return new DefaultTaskRefreshRegistry(
                List.of(stockRefresher), bondDataService, newsDataService, macroRegistry, macroFetcher,
                Optional.of(portfolioSnapshotPort), Optional.of(marketUpdatePort), Optional.of(eventPublisher));
    }

    @Test
    void shouldRunRefresherThenNotifyPortsAndPublish_onMarketRefresh() {
        // Arrange
        DefaultTaskRefreshRegistry registry = registryWithAllPorts();

        // Act
        registry.runMarketRefresh(MarketType.STOCK);

        // Assert
        verify(stockRefresher).refreshAll();
        verify(portfolioSnapshotPort).onMarketUpdate(MarketType.STOCK);
        verify(marketUpdatePort).onMarketDataUpdated(MarketType.STOCK);
        verify(eventPublisher).publish(any(DomainEvent.class));
    }

    @Test
    void shouldThrow_whenNoRefresherRegisteredForType() {
        // Arrange — only STOCK is registered; refreshing CRYPTO has no refresher.
        DefaultTaskRefreshRegistry registry = registryWithAllPorts();

        // Act + Assert
        assertThatThrownBy(() -> registry.runMarketRefresh(MarketType.CRYPTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.admin.noRefresher");
        verify(stockRefresher, never()).refreshAll();
    }

    @Test
    void shouldSwallowDownstreamFailures_andStillPublish() {
        // Arrange — both ports throw; the refresh must not propagate and the market event still publishes.
        DefaultTaskRefreshRegistry registry = registryWithAllPorts();
        doThrow(new RuntimeException("snapshot down")).when(portfolioSnapshotPort).onMarketUpdate(any());
        doThrow(new RuntimeException("cache down")).when(marketUpdatePort).onMarketDataUpdated(any());
        doThrow(new RuntimeException("kafka down")).when(eventPublisher).publish(any());

        // Act
        registry.runMarketRefresh(MarketType.STOCK);

        // Assert — refreshAll ran; the swallowed failures did not abort the method.
        verify(stockRefresher).refreshAll();
        verify(eventPublisher).publish(any());
    }

    @Test
    void shouldNoOpOptionalPorts_whenAbsent() {
        // Arrange — empty optionals: the ifPresent bodies are skipped, refresh still succeeds.
        DefaultTaskRefreshRegistry registry = new DefaultTaskRefreshRegistry(
                List.of(stockRefresher), bondDataService, newsDataService, macroRegistry, macroFetcher,
                Optional.empty(), Optional.empty(), Optional.empty());

        // Act
        registry.runMarketRefresh(MarketType.STOCK);

        // Assert
        verify(stockRefresher).refreshAll();
        verify(portfolioSnapshotPort, never()).onMarketUpdate(any());
    }

    @Test
    void shouldDelegateBondUpdate() {
        // Arrange
        DefaultTaskRefreshRegistry registry = registryWithAllPorts();

        // Act
        registry.runBondUpdate();

        // Assert
        verify(bondDataService).updateBonds();
    }

    @Test
    void shouldDelegateNewsUpdate() {
        // Arrange
        DefaultTaskRefreshRegistry registry = registryWithAllPorts();

        // Act
        registry.runNewsUpdate();

        // Assert
        verify(newsDataService).updateNews();
    }

    @Test
    void shouldSyncSynchronizeFetchAndPublishMacroEvent_whenIndicatorsChanged() {
        // Arrange — fetch reports a changed code, so a macro-updated event is published.
        DefaultTaskRefreshRegistry registry = registryWithAllPorts();
        when(macroFetcher.refreshAll())
                .thenReturn(new MacroIndicatorFetchService.FetchOutcome(1, 5, List.of("TP.CPI")));

        // Act
        registry.runMacroRefresh();

        // Assert
        verify(macroRegistry).synchronizeFromConfig();
        verify(eventPublisher).publish(any(DomainEvent.class));
    }

    @Test
    void shouldNotPublishMacroEvent_whenNothingChanged() {
        // Arrange — no changed codes means the publish branch is skipped.
        DefaultTaskRefreshRegistry registry = registryWithAllPorts();
        when(macroFetcher.refreshAll())
                .thenReturn(new MacroIndicatorFetchService.FetchOutcome(0, 0, List.of()));

        // Act
        registry.runMacroRefresh();

        // Assert
        verify(macroRegistry).synchronizeFromConfig();
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldSwallowMacroPublishFailure() {
        // Arrange — the macro event publish throws; runMacroRefresh swallows it.
        DefaultTaskRefreshRegistry registry = registryWithAllPorts();
        when(macroFetcher.refreshAll())
                .thenReturn(new MacroIndicatorFetchService.FetchOutcome(1, 5, List.of("TP.CPI")));
        doThrow(new RuntimeException("kafka down")).when(eventPublisher).publish(any());

        // Act
        registry.runMacroRefresh();

        // Assert
        verify(eventPublisher).publish(any());
    }
}
