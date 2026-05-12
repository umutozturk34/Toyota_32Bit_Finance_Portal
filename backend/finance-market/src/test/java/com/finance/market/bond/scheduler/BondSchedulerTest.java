package com.finance.market.bond.scheduler;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.model.MarketType;
import com.finance.market.bond.service.BondDataService;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BondSchedulerTest {

    @Mock private BondDataService bondDataService;
    @Mock private TaskTrackingService taskTracker;
    @Mock private ObjectProvider<EventPublisherPort> events;
    @Mock private EventPublisherPort eventPublisher;

    private BondScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BondScheduler(bondDataService, taskTracker, events);
        doAnswer(inv -> { ((Runnable) inv.getArgument(2)).run(); return null; })
                .when(taskTracker).runTracked(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void runDailyBondUpdate_invokesUpdateBondsAndPublishesEvent_whenEventPublisherAvailable() {
        doAnswer(inv -> {
            Consumer<EventPublisherPort> consumer = inv.getArgument(0);
            consumer.accept(eventPublisher);
            return null;
        }).when(events).ifAvailable(any(Consumer.class));

        scheduler.runDailyBondUpdate();

        verify(bondDataService).updateBonds();
        ArgumentCaptor<MarketUpdatedEvent> captor = ArgumentCaptor.forClass(MarketUpdatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().marketType()).isEqualTo(MarketType.BOND);
        assertThat(captor.getValue().source()).isEqualTo("scheduled-bond-daily");
    }

    @SuppressWarnings("unchecked")
    @Test
    void runDailyBondUpdate_skipsPublish_whenEventPublisherUnavailable() {
        doAnswer(inv -> null).when(events).ifAvailable(any(Consumer.class));

        scheduler.runDailyBondUpdate();

        verify(bondDataService).updateBonds();
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void runDailyBondUpdate_wrapsInTaskTrackerWithExpectedLabels() {
        scheduler.runDailyBondUpdate();

        verify(taskTracker).runTracked(eq("scheduled-bond-daily"),
                eq("Scheduled daily bond update (snapshot + rate history)"),
                any(Runnable.class));
    }
}
