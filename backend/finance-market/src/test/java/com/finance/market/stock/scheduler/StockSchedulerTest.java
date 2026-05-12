package com.finance.market.stock.scheduler;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.model.MarketType;
import com.finance.market.core.scheduler.SchedulerPorts;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.market.stock.service.StockDataService;
import com.finance.shared.service.PortfolioSnapshotPort;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockSchedulerTest {

    @Mock private StockDataService dataService;
    @Mock private TaskTrackingService taskTracker;
    @Mock private PortfolioSnapshotPort portfolioPort;
    @Mock private MarketUpdatePort marketPort;
    @Mock private EventPublisherPort eventPublisher;

    private StockScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StockScheduler(dataService, taskTracker,
                new SchedulerPorts(Optional.of(portfolioPort), Optional.of(marketPort), Optional.of(eventPublisher)));
        doAnswer(inv -> { ((Runnable) inv.getArgument(2)).run(); return null; })
                .when(taskTracker).runTracked(any(), any(), any());
    }

    @Test
    void runMorningStockUpdate_dispatchesWithMorningTaskType() {
        scheduler.runMorningStockUpdate();

        verifyDispatched("scheduled-stock-morning");
    }

    @Test
    void runAfternoonStockUpdate_dispatchesWithAfternoonTaskType() {
        scheduler.runAfternoonStockUpdate();

        verifyDispatched("scheduled-stock-afternoon");
    }

    @Test
    void runEveningStockUpdate_dispatchesWithEveningTaskType() {
        scheduler.runEveningStockUpdate();

        verifyDispatched("scheduled-stock-evening");
    }

    @Test
    void allCronMethods_invokeRefreshOncePerCall() {
        scheduler.runMorningStockUpdate();
        scheduler.runAfternoonStockUpdate();
        scheduler.runEveningStockUpdate();

        verify(dataService, times(3)).refreshAll();
    }

    private void verifyDispatched(String expectedTaskType) {
        verify(dataService).refreshAll();
        verify(portfolioPort).onMarketUpdate(MarketType.STOCK);
        verify(marketPort).onMarketDataUpdated(MarketType.STOCK);
        ArgumentCaptor<MarketUpdatedEvent> captor = ArgumentCaptor.forClass(MarketUpdatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().marketType()).isEqualTo(MarketType.STOCK);
        assertThat(captor.getValue().source()).isEqualTo(expectedTaskType);
        verify(taskTracker).runTracked(eq(expectedTaskType), any(), any());
    }
}
