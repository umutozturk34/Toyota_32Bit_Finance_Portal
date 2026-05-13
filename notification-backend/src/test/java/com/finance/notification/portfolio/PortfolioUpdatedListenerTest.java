package com.finance.notification.portfolio;

import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioUpdatedListenerTest {

    @Mock private NotificationFanoutService fanoutService;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private PortfolioSnapshotReader snapshotReader;
    @SuppressWarnings("unchecked")
    @Mock private Cache<String, Boolean> processedEventIds;
    @Mock private Acknowledgment ack;

    private PortfolioUpdatedListener listener;

    @BeforeEach
    void setUp() {
        listener = new PortfolioUpdatedListener(fanoutService, preferences, snapshotReader, processedEventIds);
    }

    private PortfolioUpdatedEvent event() {
        return new PortfolioUpdatedEvent("evt-1", OffsetDateTime.now(), "scheduler");
    }

    @Test
    void onPortfolioUpdated_skips_whenEventAlreadyProcessed() {
        PortfolioUpdatedEvent event = event();
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(Boolean.TRUE);

        listener.onPortfolioUpdated(event, ack);

        verify(fanoutService, never()).fanoutBulk(anyString(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onPortfolioUpdated_invokesFanout_andMarksEventProcessed() {
        PortfolioUpdatedEvent event = event();
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(null);
        when(fanoutService.fanoutBulk(anyString(), any(), any()))
                .thenReturn(new NotificationFanoutService.FanoutResult(5, 0));

        listener.onPortfolioUpdated(event, ack);

        verify(fanoutService).fanoutBulk(anyString(), any(), any());
        verify(processedEventIds).put("evt-1", Boolean.TRUE);
        verify(ack).acknowledge();
    }
}
