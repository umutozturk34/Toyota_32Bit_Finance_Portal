package com.finance.notification.market;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketSessionTransitionListenerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-05T11:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock private MarketSessionResolver resolver;
    @Mock private NotificationDispatcher dispatcher;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private Acknowledgment ack;

    private MarketSessionTransitionListener listener(MarketSessionTracker tracker) {
        return new MarketSessionTransitionListener(resolver, tracker, dispatcher, preferences, FIXED_CLOCK);
    }

    private NotificationPreference subscriberWithMarkets(String userSub, String markets) {
        return NotificationPreference.builder()
                .userSub(userSub)
                .marketSessionMarkets(markets)
                .build();
    }

    @Test
    void should_fireMarketOpenedAndDataUpdated_when_transitionFromClosedToOpen() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        tracker.update(SessionMarket.STOCK, MarketSession.CLOSED);
        when(resolver.resolve(SessionMarket.STOCK, FIXED_NOW)).thenReturn(Optional.of(MarketSession.OPEN));
        when(preferences.findAll()).thenReturn(List.of(subscriberWithMarkets("user-1", "STOCK,FOREX")));

        listener(tracker).onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduler"), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(2)).dispatch(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(r -> r.payload().type().name())
                .containsExactlyInAnyOrder("MARKET_OPENED", "MARKET_DATA_UPDATED");
        verify(ack).acknowledge();
    }

    @Test
    void should_fireOnlyDataUpdated_when_alreadyOpenAndNoTransition() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        tracker.update(SessionMarket.STOCK, MarketSession.OPEN);
        when(resolver.resolve(SessionMarket.STOCK, FIXED_NOW)).thenReturn(Optional.of(MarketSession.OPEN));
        when(preferences.findAll()).thenReturn(List.of(subscriberWithMarkets("user-1", "STOCK")));

        listener(tracker).onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduler"), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(1)).dispatch(captor.capture());
        assertThat(captor.getValue().payload().type().name()).isEqualTo("MARKET_DATA_UPDATED");
    }

    @Test
    void should_skipUser_when_marketNotInSelection() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        tracker.update(SessionMarket.STOCK, MarketSession.CLOSED);
        when(resolver.resolve(SessionMarket.STOCK, FIXED_NOW)).thenReturn(Optional.of(MarketSession.OPEN));
        when(preferences.findAll()).thenReturn(List.of(
                subscriberWithMarkets("user-1", "FUND,COMMODITY"),
                subscriberWithMarkets("user-2", "STOCK")));

        listener(tracker).onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduler"), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(2)).dispatch(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationRequest::userSub)
                .containsOnly("user-2");
    }

    @Test
    void should_skipMarketOpened_when_trackerEmptyOnFirstObservation() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        when(resolver.resolve(SessionMarket.CRYPTO, FIXED_NOW)).thenReturn(Optional.of(MarketSession.OPEN));
        when(preferences.findAll()).thenReturn(List.of(subscriberWithMarkets("user-1", "CRYPTO")));

        listener(tracker).onMarketUpdated(MarketUpdatedEvent.of(MarketType.CRYPTO, "scheduled-crypto-morning"), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(1)).dispatch(captor.capture());
        assertThat(captor.getValue().payload().type().name()).isEqualTo("MARKET_DATA_UPDATED");
    }

    @Test
    void should_acknowledgeEvenWhenDispatcherThrows() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        when(resolver.resolve(SessionMarket.STOCK, FIXED_NOW)).thenReturn(Optional.of(MarketSession.OPEN));
        when(preferences.findAll()).thenThrow(new RuntimeException("DB down"));

        listener(tracker).onMarketUpdated(MarketUpdatedEvent.of(MarketType.STOCK, "scheduler"), ack);

        verify(ack).acknowledge();
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void should_dispatchDataUpdatedForCryptoButNeverOpened_when_marketIs24x7() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        tracker.update(SessionMarket.CRYPTO, MarketSession.OPEN);
        when(resolver.resolve(SessionMarket.CRYPTO, FIXED_NOW)).thenReturn(Optional.of(MarketSession.OPEN));
        when(preferences.findAll()).thenReturn(List.of(subscriberWithMarkets("user-1", "CRYPTO")));

        listener(tracker).onMarketUpdated(MarketUpdatedEvent.of(MarketType.CRYPTO, "scheduler"), ack);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(1)).dispatch(captor.capture());
        assertThat(captor.getValue().payload().type().name()).isEqualTo("MARKET_DATA_UPDATED");
        verify(ack).acknowledge();
    }
}
