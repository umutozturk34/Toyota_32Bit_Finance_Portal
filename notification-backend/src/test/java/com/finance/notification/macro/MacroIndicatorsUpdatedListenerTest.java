package com.finance.notification.macro;

import com.finance.common.event.MacroIndicatorsUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.macro.MacroIndicatorChangeReader.IndicatorChange;
import com.finance.notification.macro.MacroIndicatorChangeReader.IndicatorChange.Direction;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroIndicatorsUpdatedListenerTest {

    @Mock private NotificationFanoutService fanoutService;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private MacroIndicatorChangeReader changeReader;
    @SuppressWarnings("unchecked")
    @Mock private Cache<String, Boolean> processedEventIds;
    @Mock private Acknowledgment ack;

    private MacroIndicatorsUpdatedListener listener;

    @BeforeEach
    void setUp() {
        listener = new MacroIndicatorsUpdatedListener(fanoutService, preferences, changeReader, processedEventIds);
    }

    private MacroIndicatorsUpdatedEvent event() {
        return new MacroIndicatorsUpdatedEvent("evt-1", OffsetDateTime.now(), "scheduled-macro-daily",
                List.of("TP.RATE"));
    }

    private IndicatorChange change() {
        return new IndicatorChange("TP.RATE", "CPI", "INFLATION", "PERCENT", null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("40.50"),
                LocalDate.of(2026, 5, 19), new BigDecimal("40.00"),
                new BigDecimal("0.50"), new BigDecimal("1.25"), Direction.UP);
    }

    @Test
    void should_skipFanout_when_eventAlreadyProcessed() {
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(Boolean.TRUE);

        listener.onMacroIndicatorsUpdated(event(), ack);

        verify(fanoutService, never()).fanout(anyString(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void should_skipFanoutButMarkProcessed_when_noActualDeltas() {
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(null);
        when(changeReader.findChanges(List.of("TP.RATE"))).thenReturn(List.of());

        listener.onMacroIndicatorsUpdated(event(), ack);

        verify(fanoutService, never()).fanout(anyString(), any(), any());
        verify(processedEventIds).put("evt-1", Boolean.TRUE);
        verify(ack).acknowledge();
    }

    @Test
    void should_invokeFanoutAndMarkProcessed_when_changesPresent() {
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(null);
        when(changeReader.findChanges(List.of("TP.RATE"))).thenReturn(List.of(change()));
        when(fanoutService.fanout(anyString(), any(), any())).thenReturn(new FanoutResult(7, 1));

        listener.onMacroIndicatorsUpdated(event(), ack);

        verify(fanoutService).fanout(anyString(), any(), any());
        verify(processedEventIds).put("evt-1", Boolean.TRUE);
        verify(ack).acknowledge();
    }
}
