package com.finance.market.crypto.scheduler;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.model.MarketType;
import com.finance.market.core.scheduler.SchedulerPorts;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.market.crypto.service.CryptoDataService;
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
class CryptoSchedulerTest {

    @Mock private CryptoDataService dataService;
    @Mock private TaskTrackingService taskTracker;
    @Mock private PortfolioSnapshotPort portfolioPort;
    @Mock private MarketUpdatePort marketPort;
    @Mock private EventPublisherPort eventPublisher;

    private CryptoScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CryptoScheduler(dataService, taskTracker,
                new SchedulerPorts(Optional.of(portfolioPort), Optional.of(marketPort), Optional.of(eventPublisher)));
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(2)).run();
            return null;
        }).when(taskTracker).runTracked(any(), any(), any());
    }

    @Test
    void runMorningCryptoUpdate_invokesRefreshAndPortsAndEventWithMorningTask() {
        scheduler.runMorningCryptoUpdate();

        verifyDispatched("scheduled-crypto-morning");
    }

    @Test
    void runAfternoonCryptoUpdate_invokesRefreshAndEventWithAfternoonTask() {
        scheduler.runAfternoonCryptoUpdate();

        verifyDispatched("scheduled-crypto-afternoon");
    }

    @Test
    void runEveningCryptoUpdate_invokesRefreshAndEventWithEveningTask() {
        scheduler.runEveningCryptoUpdate();

        verifyDispatched("scheduled-crypto-evening");
    }

    @Test
    void allCronMethods_invokeDataServiceExactlyOncePerCall() {
        scheduler.runMorningCryptoUpdate();
        scheduler.runAfternoonCryptoUpdate();
        scheduler.runEveningCryptoUpdate();

        verify(dataService, times(3)).refreshAll();
    }

    private void verifyDispatched(String expectedTaskType) {
        verify(dataService).refreshAll();
        verify(portfolioPort).onMarketUpdate(MarketType.CRYPTO);
        verify(marketPort).onMarketDataUpdated(MarketType.CRYPTO);
        ArgumentCaptor<MarketUpdatedEvent> captor = ArgumentCaptor.forClass(MarketUpdatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().marketType()).isEqualTo(MarketType.CRYPTO);
        assertThat(captor.getValue().source()).isEqualTo(expectedTaskType);
        verify(taskTracker).runTracked(eq(expectedTaskType), any(), any());
    }
}
