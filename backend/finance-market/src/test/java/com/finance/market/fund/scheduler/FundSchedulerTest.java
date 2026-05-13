package com.finance.market.fund.scheduler;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.model.MarketType;
import com.finance.market.core.scheduler.SchedulerPorts;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.market.fund.service.FundDataService;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FundSchedulerTest {

    @Mock private FundDataService dataService;
    @Mock private TaskTrackingService taskTracker;
    @Mock private PortfolioSnapshotPort portfolioPort;
    @Mock private MarketUpdatePort marketPort;
    @Mock private EventPublisherPort eventPublisher;

    private FundScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new FundScheduler(dataService, taskTracker,
                new SchedulerPorts(Optional.of(portfolioPort), Optional.of(marketPort), Optional.of(eventPublisher)));
        doAnswer(inv -> { ((Runnable) inv.getArgument(2)).run(); return null; })
                .when(taskTracker).runTracked(any(), any(), any());
    }

    @Test
    void runDailyFundUpdate_invokesRefreshAndNotifiesPorts() {
        scheduler.runDailyFundUpdate();

        verify(dataService).refreshAll();
        verify(portfolioPort).onMarketUpdate(MarketType.FUND);
        verify(marketPort).onMarketDataUpdated(MarketType.FUND);
    }

    @Test
    void runDailyFundUpdate_publishesFundMarketUpdatedEvent() {
        scheduler.runDailyFundUpdate();

        ArgumentCaptor<MarketUpdatedEvent> captor = ArgumentCaptor.forClass(MarketUpdatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().marketType()).isEqualTo(MarketType.FUND);
        assertThat(captor.getValue().source()).isEqualTo("scheduled-fund-full");
    }
}
