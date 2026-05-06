package com.finance.notification.market;

import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketSessionSchedulerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-05T07:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock private MarketSessionResolver resolver;
    @Mock private NotificationDispatcher dispatcher;
    @Mock private NotificationPreferenceRepository preferences;

    private MarketSessionScheduler scheduler(MarketSessionTracker tracker) {
        return new MarketSessionScheduler(resolver, tracker, dispatcher, preferences, FIXED_CLOCK);
    }

    private NotificationPreference subscriberWithMarkets(String userSub, String markets) {
        return NotificationPreference.builder()
                .userSub(userSub)
                .marketSessionMarkets(markets)
                .build();
    }

    private void resolverReturnsClosedForAllExcept(SessionMarket open) {
        for (SessionMarket m : SessionMarket.values()) {
            when(resolver.resolve(eq(m), any())).thenReturn(Optional.of(
                    m == open ? MarketSession.OPEN : MarketSession.CLOSED));
        }
    }

    @Test
    void should_dispatchMarketOpened_when_stockTransitionsFromClosedToOpen() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        for (SessionMarket m : SessionMarket.values()) {
            tracker.update(m, MarketSession.CLOSED);
        }
        resolverReturnsClosedForAllExcept(SessionMarket.STOCK);
        when(preferences.findAll()).thenReturn(List.of(subscriberWithMarkets("user-1", "STOCK")));

        scheduler(tracker).scanTransitions();

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(1)).dispatch(captor.capture());
        assertThat(captor.getValue().payload().type().name()).isEqualTo("MARKET_OPENED");
    }

    @Test
    void should_dispatchMarketClosed_when_stockTransitionsFromOpenToClosed() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        for (SessionMarket m : SessionMarket.values()) {
            tracker.update(m, MarketSession.OPEN);
        }
        for (SessionMarket m : SessionMarket.values()) {
            when(resolver.resolve(eq(m), any())).thenReturn(Optional.of(
                    m == SessionMarket.STOCK ? MarketSession.CLOSED : MarketSession.OPEN));
        }
        when(preferences.findAll()).thenReturn(List.of(subscriberWithMarkets("user-1", "STOCK")));

        scheduler(tracker).scanTransitions();

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(1)).dispatch(captor.capture());
        assertThat(captor.getValue().payload().type().name()).isEqualTo("MARKET_CLOSED");
    }

    @Test
    void should_skipFirstObservation_when_trackerEmptyForMarket() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        when(resolver.resolve(any(), any())).thenReturn(Optional.of(MarketSession.OPEN));

        scheduler(tracker).scanTransitions();

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void should_skipUser_when_marketSelectionIsBlank() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        for (SessionMarket m : SessionMarket.values()) {
            tracker.update(m, MarketSession.CLOSED);
        }
        resolverReturnsClosedForAllExcept(SessionMarket.STOCK);
        when(preferences.findAll()).thenReturn(List.of(
                subscriberWithMarkets("user-blank", ""),
                subscriberWithMarkets("user-null", null)));

        scheduler(tracker).scanTransitions();

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void should_loadPreferencesOnce_when_multipleMarketsTransitionInSameTick() {
        MarketSessionTracker tracker = new MarketSessionTracker();
        for (SessionMarket m : SessionMarket.values()) {
            tracker.update(m, MarketSession.CLOSED);
        }
        when(resolver.resolve(any(), any())).thenReturn(Optional.of(MarketSession.OPEN));
        when(preferences.findAll()).thenReturn(List.of(subscriberWithMarkets("user-1", "STOCK,FOREX,FUND")));

        scheduler(tracker).scanTransitions();

        verify(preferences, times(1)).findAll();
    }
}
