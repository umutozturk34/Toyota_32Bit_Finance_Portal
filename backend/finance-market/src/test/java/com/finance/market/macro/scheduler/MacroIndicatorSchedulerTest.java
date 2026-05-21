package com.finance.market.macro.scheduler;

import com.finance.common.event.MacroIndicatorsUpdatedEvent;
import com.finance.market.macro.service.MacroIndicatorFetchService;
import com.finance.market.macro.service.MacroIndicatorRegistryService;
import com.finance.shared.event.EventPublisherPort;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroIndicatorSchedulerTest {

    @Mock private MacroIndicatorRegistryService registry;
    @Mock private MacroIndicatorFetchService fetcher;
    @Mock private TaskTrackingService taskTracker;
    @Mock private org.springframework.beans.factory.ObjectProvider<EventPublisherPort> events;
    @Mock private EventPublisherPort eventPublisher;

    private MacroIndicatorScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MacroIndicatorScheduler(registry, fetcher, taskTracker, events);
        org.mockito.Mockito.doAnswer(inv -> {
            ((Runnable) inv.getArgument(2)).run();
            return null;
        }).when(taskTracker).runTracked(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    void should_publishEvent_when_someIndicatorsChanged() {
        when(fetcher.refreshAll()).thenReturn(
                new MacroIndicatorFetchService.FetchOutcome(2, 3, List.of("TP.CPI", "TP.RATE")));
        captureEventsConsumer();

        scheduler.runDailyRefresh();

        ArgumentCaptor<MacroIndicatorsUpdatedEvent> captor =
                ArgumentCaptor.forClass(MacroIndicatorsUpdatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().changedCodes()).containsExactly("TP.CPI", "TP.RATE");
        assertThat(captor.getValue().source()).isEqualTo("scheduled-macro-daily");
    }

    @Test
    void should_skipEvent_when_nothingChanged() {
        when(fetcher.refreshAll()).thenReturn(
                new MacroIndicatorFetchService.FetchOutcome(2, 0, List.of()));

        scheduler.runDailyRefresh();

        verify(events, never()).ifAvailable(any());
    }

    private void captureEventsConsumer() {
        org.mockito.Mockito.doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<EventPublisherPort> consumer = inv.getArgument(0);
            consumer.accept(eventPublisher);
            return null;
        }).when(events).ifAvailable(any());
    }
}
