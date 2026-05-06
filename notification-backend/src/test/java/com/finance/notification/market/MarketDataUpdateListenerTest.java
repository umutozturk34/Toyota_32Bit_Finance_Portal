package com.finance.notification.market;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataUpdateListenerTest {

    @Mock private NotificationDispatcher dispatcher;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private Acknowledgment ack;

    private MarketDataUpdateListener listener() {
        Cache<String, Boolean> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(1_000)
                .build();
        return new MarketDataUpdateListener(dispatcher, preferences, cache);
    }

    private NotificationPreference subscriberWithMarkets(String userSub, String markets) {
        return NotificationPreference.builder()
                .userSub(userSub)
                .marketSessionMarkets(markets)
                .build();
    }

    @Test
    void should_dispatchDataUpdatedForOptedInUser_when_kafkaEventArrives() {
        when(preferences.findAll()).thenReturn(List.of(
                subscriberWithMarkets("user-1", "STOCK,FUND"),
                subscriberWithMarkets("user-2", "FOREX")));

        listener().onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduled-stock-morning"), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(1)).dispatch(captor.capture());
        assertThat(captor.getValue().userSub()).isEqualTo("user-1");
        assertThat(captor.getValue().payload().type().name()).isEqualTo("MARKET_DATA_UPDATED");
        verify(ack).acknowledge();
    }

    @Test
    void should_skipAllUsers_when_marketSelectionIsBlankOrNull() {
        when(preferences.findAll()).thenReturn(List.of(
                subscriberWithMarkets("user-blank", ""),
                subscriberWithMarkets("user-null", null)));

        listener().onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduler"), ack);

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void should_propagateExceptionWithoutAcking_when_transientFailureOccurs() {
        when(preferences.findAll()).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() ->
                listener().onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduler"), ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");

        verify(ack, never()).acknowledge();
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void should_dispatchOnce_when_sameEventIdRedeliveredTwice() {
        when(preferences.findAll()).thenReturn(List.of(subscriberWithMarkets("user-1", "STOCK")));
        MarketUpdatedEvent event = MarketUpdatedEvent.of(MarketType.STOCK, "scheduled-stock-morning");
        MarketDataUpdateListener subject = listener();

        subject.onMarketUpdated(event, ack);
        subject.onMarketUpdated(event, ack);

        verify(dispatcher, times(1)).dispatch(any());
        verify(ack, times(2)).acknowledge();
    }
}
