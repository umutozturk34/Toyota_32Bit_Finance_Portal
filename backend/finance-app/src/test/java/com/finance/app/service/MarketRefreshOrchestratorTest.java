package com.finance.app.service;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.MarketRefresher;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.shared.event.EventPublisherPort;
import com.finance.shared.service.PortfolioSnapshotPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketRefreshOrchestratorTest {

    @Mock private MarketRefresher stockRefresher;
    @Mock private MarketRefresher cryptoRefresher;
    @Mock private PortfolioSnapshotPort portfolioSnapshotPort;
    @Mock private MarketUpdatePort marketUpdatePort;
    @Mock private EventPublisherPort eventPublisher;

    private MarketRefreshOrchestrator newOrchestrator(List<MarketRefresher> refreshers,
                                                     Optional<PortfolioSnapshotPort> portfolio,
                                                     Optional<MarketUpdatePort> cache,
                                                     Optional<EventPublisherPort> publisher) {
        when(stockRefresher.getMarketType()).thenReturn(MarketType.STOCK);
        when(cryptoRefresher.getMarketType()).thenReturn(MarketType.CRYPTO);
        return new MarketRefreshOrchestrator(refreshers, portfolio, cache, publisher);
    }

    @Test
    void shouldRefreshAllAndNotifyAllPorts_whenRefresherExists() {
        MarketRefreshOrchestrator orchestrator = newOrchestrator(
                List.of(stockRefresher, cryptoRefresher),
                Optional.of(portfolioSnapshotPort),
                Optional.of(marketUpdatePort),
                Optional.of(eventPublisher));

        orchestrator.refresh(MarketType.STOCK);

        verify(stockRefresher).refreshAll();
        verify(cryptoRefresher, never()).refreshAll();
        verify(portfolioSnapshotPort).onMarketUpdate(MarketType.STOCK);
        verify(marketUpdatePort).onMarketDataUpdated(MarketType.STOCK);
        ArgumentCaptor<MarketUpdatedEvent> captor = ArgumentCaptor.forClass(MarketUpdatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().marketType()).isEqualTo(MarketType.STOCK);
        assertThat(captor.getValue().source()).isEqualTo("admin");
    }

    @Test
    void shouldThrowIllegalArgument_whenNoRefresherForMarketType() {
        MarketRefreshOrchestrator orchestrator = newOrchestrator(
                List.of(stockRefresher),
                Optional.of(portfolioSnapshotPort),
                Optional.of(marketUpdatePort),
                Optional.of(eventPublisher));

        assertThatThrownBy(() -> orchestrator.refresh(MarketType.BOND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.admin.noRefresher");
        verify(stockRefresher, never()).refreshAll();
        verify(portfolioSnapshotPort, never()).onMarketUpdate(any());
        verify(marketUpdatePort, never()).onMarketDataUpdated(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldSkipOptionalPorts_whenAllAreEmpty() {
        MarketRefreshOrchestrator orchestrator = newOrchestrator(
                List.of(stockRefresher),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        orchestrator.refresh(MarketType.STOCK);

        verify(stockRefresher).refreshAll();
        verify(portfolioSnapshotPort, never()).onMarketUpdate(any());
        verify(marketUpdatePort, never()).onMarketDataUpdated(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldSwallowException_whenPortfolioSnapshotFails() {
        MarketRefreshOrchestrator orchestrator = newOrchestrator(
                List.of(stockRefresher),
                Optional.of(portfolioSnapshotPort),
                Optional.of(marketUpdatePort),
                Optional.of(eventPublisher));
        doThrow(new RuntimeException("portfolio down")).when(portfolioSnapshotPort)
                .onMarketUpdate(MarketType.STOCK);

        orchestrator.refresh(MarketType.STOCK);

        verify(stockRefresher).refreshAll();
        verify(marketUpdatePort).onMarketDataUpdated(MarketType.STOCK);
        verify(eventPublisher).publish(any(MarketUpdatedEvent.class));
    }

    @Test
    void shouldSwallowException_whenMarketCacheUpdateFails() {
        MarketRefreshOrchestrator orchestrator = newOrchestrator(
                List.of(stockRefresher),
                Optional.of(portfolioSnapshotPort),
                Optional.of(marketUpdatePort),
                Optional.of(eventPublisher));
        doThrow(new RuntimeException("cache down")).when(marketUpdatePort)
                .onMarketDataUpdated(MarketType.STOCK);

        orchestrator.refresh(MarketType.STOCK);

        verify(stockRefresher).refreshAll();
        verify(portfolioSnapshotPort).onMarketUpdate(MarketType.STOCK);
        verify(eventPublisher).publish(any(MarketUpdatedEvent.class));
    }

    @Test
    void shouldSwallowException_whenEventPublishFails() {
        MarketRefreshOrchestrator orchestrator = newOrchestrator(
                List.of(stockRefresher),
                Optional.of(portfolioSnapshotPort),
                Optional.of(marketUpdatePort),
                Optional.of(eventPublisher));
        doThrow(new RuntimeException("publish down")).when(eventPublisher)
                .publish(any(MarketUpdatedEvent.class));

        orchestrator.refresh(MarketType.STOCK);

        verify(stockRefresher).refreshAll();
        verify(portfolioSnapshotPort).onMarketUpdate(MarketType.STOCK);
        verify(marketUpdatePort).onMarketDataUpdated(MarketType.STOCK);
    }

    @Test
    void shouldRouteToCorrectRefresher_whenMultipleRefreshersRegistered() {
        MarketRefreshOrchestrator orchestrator = newOrchestrator(
                List.of(stockRefresher, cryptoRefresher),
                Optional.of(portfolioSnapshotPort),
                Optional.of(marketUpdatePort),
                Optional.of(eventPublisher));

        orchestrator.refresh(MarketType.CRYPTO);

        verify(cryptoRefresher).refreshAll();
        verify(stockRefresher, never()).refreshAll();
        verify(portfolioSnapshotPort).onMarketUpdate(MarketType.CRYPTO);
    }
}
