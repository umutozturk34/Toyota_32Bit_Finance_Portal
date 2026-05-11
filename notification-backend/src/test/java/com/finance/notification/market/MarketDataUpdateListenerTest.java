package com.finance.notification.market;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationPersister;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.Prepared;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataUpdateListenerTest {

    @Mock private NotificationDispatcher dispatcher;
    @Mock private NotificationPersister persister;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private Acknowledgment ack;

    private MarketDataUpdateListener listener() {
        Cache<String, Boolean> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(1_000)
                .build();
        NotificationDispatchProperties dispatchProperties = new NotificationDispatchProperties(
                null, null, new NotificationDispatchProperties.Fanout(200));
        NotificationFanoutService fanoutService = new NotificationFanoutService(
                dispatcher, persister, dispatchProperties);
        return new MarketDataUpdateListener(fanoutService, preferences, cache);
    }

    private NotificationPreference subscriber(String userSub) {
        return NotificationPreference.builder().userSub(userSub).build();
    }

    private Page<NotificationPreference> pageOf(NotificationPreference... prefs) {
        return new PageImpl<>(List.of(prefs));
    }

    @Test
    void should_dispatchDataUpdatedForOptedInUser_when_kafkaEventArrives() {
        when(preferences.findMarketDataSubscribed(eq("STOCK"), any(Pageable.class)))
                .thenReturn(pageOf(subscriber("user-1")));
        when(dispatcher.prepare(any(NotificationRequest.class), any()))
                .thenReturn(Optional.of(new Prepared("user-1", null, null)));

        listener().onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduled-stock-morning"), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(1)).prepare(captor.capture(), any());
        assertThat(captor.getValue().userSub()).isEqualTo("user-1");
        assertThat(captor.getValue().payload().type().name()).isEqualTo("MARKET_DATA_UPDATED");
        verify(persister).persistBatch(any());
        verify(ack).acknowledge();
    }

    @Test
    void should_skipAllUsers_when_subscriberPageIsEmpty() {
        when(preferences.findMarketDataSubscribed(eq("STOCK"), any(Pageable.class)))
                .thenReturn(Page.empty());

        listener().onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduler"), ack);

        verify(dispatcher, never()).prepare(any(), any());
        verify(persister, never()).persistBatch(any());
        verify(ack).acknowledge();
    }

    @Test
    void should_propagateExceptionWithoutAcking_when_transientFailureOccurs() {
        when(preferences.findMarketDataSubscribed(eq("STOCK"), any(Pageable.class)))
                .thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() ->
                listener().onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduler"), ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");

        verify(ack, never()).acknowledge();
        verify(dispatcher, never()).prepare(any(), any());
    }

    @Test
    void should_dispatchOnce_when_sameEventIdRedeliveredTwice() {
        when(preferences.findMarketDataSubscribed(eq("STOCK"), any(Pageable.class)))
                .thenReturn(pageOf(subscriber("user-1")));
        when(dispatcher.prepare(any(NotificationRequest.class), any()))
                .thenReturn(Optional.of(new Prepared("user-1", null, null)));
        MarketUpdatedEvent event = MarketUpdatedEvent.of(MarketType.STOCK, "scheduled-stock-morning");
        MarketDataUpdateListener subject = listener();

        subject.onMarketUpdated(event, ack);
        subject.onMarketUpdated(event, ack);

        verify(dispatcher, times(1)).prepare(any(), any());
        verify(ack, times(2)).acknowledge();
    }
}
