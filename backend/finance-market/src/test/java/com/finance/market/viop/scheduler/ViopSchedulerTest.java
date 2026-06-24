package com.finance.market.viop.scheduler;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.market.core.scheduler.SchedulerPorts;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.market.viop.service.ViopDataService;
import com.finance.shared.event.EventPublisherPort;
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
class ViopSchedulerTest {

    @Mock private ViopDataService dataService;
    @Mock private TaskTrackingService taskTracker;
    @Mock private PortfolioSnapshotPort portfolioPort;
    @Mock private MarketUpdatePort marketPort;
    @Mock private EventPublisherPort eventPublisher;

    private ViopScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ViopScheduler(dataService, taskTracker,
                new SchedulerPorts(Optional.of(portfolioPort), Optional.of(marketPort), Optional.of(eventPublisher)));
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(2)).run();
            return null;
        }).when(taskTracker).runTracked(any(), any(), any());
    }

    @Test
    void should_dispatchOpenTaskAndHooks_when_openUpdateRuns() {
        scheduler.runOpenUpdate();

        verifyDispatched("scheduled-viop-open");
    }

    @Test
    void should_dispatchMiddayTaskAndHooks_when_middayUpdateRuns() {
        scheduler.runMiddayUpdate();

        verifyDispatched("scheduled-viop-midday");
    }

    @Test
    void should_dispatchAfternoonTaskAndHooks_when_afternoonUpdateRuns() {
        scheduler.runAfternoonUpdate();

        verifyDispatched("scheduled-viop-afternoon");
    }

    @Test
    void should_dispatchCloseTaskAndHooks_when_closeUpdateRuns() {
        scheduler.runCloseUpdate();

        verifyDispatched("scheduled-viop-close");
    }

    @Test
    void should_refreshExactlyOncePerCronMethod_when_allCronMethodsInvoked() {
        scheduler.runOpenUpdate();
        scheduler.runMiddayUpdate();
        scheduler.runAfternoonUpdate();
        scheduler.runCloseUpdate();

        verify(dataService, times(4)).refreshAll();
    }

    private void verifyDispatched(String expectedTaskType) {
        verify(dataService).refreshAll();
        verify(portfolioPort).onMarketUpdate(MarketType.VIOP);
        verify(marketPort).onMarketDataUpdated(MarketType.VIOP);
        ArgumentCaptor<MarketUpdatedEvent> captor = ArgumentCaptor.forClass(MarketUpdatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().marketType()).isEqualTo(MarketType.VIOP);
        assertThat(captor.getValue().source()).isEqualTo(expectedTaskType);
        verify(taskTracker).runTracked(eq(expectedTaskType), any(), any());
    }
}
