package com.finance.app.service;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.market.bond.service.BondDataService;
import com.finance.market.core.service.MarketRefresher;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.news.service.article.NewsDataService;
import com.finance.shared.dto.response.TaskTriggerResponse;
import com.finance.shared.event.EventPublisherPort;
import com.finance.shared.service.PortfolioSnapshotPort;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminTaskServiceTest {

    @Mock private MarketRefresher stockRefresher;
    @Mock private BondDataService bondDataService;
    @Mock private NewsDataService newsDataService;
    @Mock private TaskTrackingService taskTracker;
    @Mock private PortfolioSnapshotPort portfolioSnapshotPort;
    @Mock private MarketUpdatePort marketUpdatePort;
    @Mock private EventPublisherPort eventPublisher;

    private Executor inline;
    private AdminTaskService service;

    @BeforeEach
    void setUp() {
        when(stockRefresher.getMarketType()).thenReturn(MarketType.STOCK);
        when(taskTracker.startTask(anyString(), anyString()))
                .thenReturn(new TaskTrackingService.TaskInfo("t", "STARTED", "msg",
                        Instant.now(), null, null));
        inline = Runnable::run;
        service = new AdminTaskService(List.of(stockRefresher), bondDataService, newsDataService,
                taskTracker, inline,
                Optional.of(portfolioSnapshotPort),
                Optional.of(marketUpdatePort),
                Optional.of(eventPublisher));
    }

    @Test
    void triggerSnapshot_refreshesAllAndPublishesEvent() {
        TaskTriggerResponse response = service.triggerSnapshot(MarketType.STOCK);

        assertThat(response).isNotNull();
        verify(stockRefresher).refreshAll();
        verify(portfolioSnapshotPort).onMarketUpdate(MarketType.STOCK);
        verify(marketUpdatePort).onMarketDataUpdated(MarketType.STOCK);
        ArgumentCaptor<MarketUpdatedEvent> captor = ArgumentCaptor.forClass(MarketUpdatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().marketType()).isEqualTo(MarketType.STOCK);
    }

    @Test
    void triggerCandles_callsRefresherAndTracker() {
        service.triggerCandles(MarketType.STOCK);

        verify(stockRefresher).refreshAll();
        verify(taskTracker).completeTask(eq("stock-candles"), any());
    }

    @Test
    void triggerFull_invokesRefresh_andCompletes() {
        service.triggerFull(MarketType.STOCK);

        verify(stockRefresher).refreshAll();
        verify(taskTracker).completeTask(eq("stock-full"), any());
    }

    @Test
    void triggerSnapshot_marksFailed_whenNoRefresherForType() {
        service.triggerSnapshot(MarketType.CRYPTO);

        verify(taskTracker).failTask(eq("crypto-snapshot"), any(), anyString());
    }

    @Test
    void triggerSnapshot_swallowsPortfolioSnapshotException_andContinues() {
        org.mockito.Mockito.doThrow(new RuntimeException("portfolio down"))
                .when(portfolioSnapshotPort).onMarketUpdate(MarketType.STOCK);

        service.triggerSnapshot(MarketType.STOCK);

        verify(taskTracker).completeTask(eq("stock-snapshot"), any());
    }

    @Test
    void triggerSnapshot_swallowsMarketUpdateException_andContinues() {
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(marketUpdatePort).onMarketDataUpdated(MarketType.STOCK);

        service.triggerSnapshot(MarketType.STOCK);

        verify(taskTracker).completeTask(eq("stock-snapshot"), any());
    }

    @Test
    void triggerSnapshot_swallowsEventPublishException_andContinues() {
        org.mockito.Mockito.doThrow(new RuntimeException("kafka down"))
                .when(eventPublisher).publish(any());

        service.triggerSnapshot(MarketType.STOCK);

        verify(taskTracker).completeTask(eq("stock-snapshot"), any());
    }

    @Test
    void triggerBondUpdate_callsBondDataService() {
        service.triggerBondUpdate();

        verify(bondDataService).updateBonds();
        verify(taskTracker).completeTask(eq("bond-update"), any());
    }

    @Test
    void triggerNewsUpdate_callsNewsDataService() {
        service.triggerNewsUpdate();

        verify(newsDataService).updateNews();
        verify(taskTracker).completeTask(eq("news-update"), any());
    }

    @Test
    void executeTask_marksTaskAsFailed_whenRunnableThrows() {
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(bondDataService).updateBonds();

        service.triggerBondUpdate();

        verify(taskTracker).failTask(eq("bond-update"), any(), anyString());
        verify(taskTracker, never()).completeTask(anyString(), any());
    }
}
