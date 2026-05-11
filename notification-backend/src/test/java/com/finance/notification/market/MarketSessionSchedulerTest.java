package com.finance.notification.market;

import com.finance.notification.config.NotificationCacheProperties;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.dispatch.payload.NotificationPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.market.session.MarketSession;
import com.finance.notification.market.session.MarketSessionResolver;
import com.finance.notification.market.session.MarketSessionScheduler;
import com.finance.notification.market.session.MarketSessionTracker;
import com.finance.notification.market.session.SessionMarket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock private NotificationFanoutService fanoutService;
    @Mock private NotificationPreferenceRepository preferences;

    private MarketSessionScheduler scheduler(MarketSessionTracker tracker) {
        return new MarketSessionScheduler(resolver, tracker, fanoutService, preferences, FIXED_CLOCK);
    }

    private MarketSessionTracker freshTracker() {
        return new MarketSessionTracker(new NotificationCacheProperties(50_000L, 50_000L, 64L));
    }

    private void seedTracker(MarketSessionTracker tracker, MarketSession baseline) {
        for (SessionMarket m : SessionMarket.values()) tracker.update(m, baseline);
    }

    private void resolverReturnsExcept(SessionMarket flipped, MarketSession flippedState, MarketSession otherState) {
        for (SessionMarket m : SessionMarket.values()) {
            when(resolver.resolve(eq(m), any())).thenReturn(Optional.of(m == flipped ? flippedState : otherState));
        }
    }

    @Test
    void should_fanoutMarketOpened_when_stockTransitionsFromClosedToOpen() {
        MarketSessionTracker tracker = freshTracker();
        seedTracker(tracker, MarketSession.CLOSED);
        resolverReturnsExcept(SessionMarket.STOCK, MarketSession.OPEN, MarketSession.CLOSED);
        when(fanoutService.fanout(anyString(), any(), any())).thenReturn(new FanoutResult(0, 0));

        scheduler(tracker).scanTransitions();

        ArgumentCaptor<String> labelCaptor = ArgumentCaptor.forClass(String.class);
        verify(fanoutService, times(1)).fanout(labelCaptor.capture(), any(), any());
        assertThat(labelCaptor.getValue()).contains("stock").contains("open");
    }

    @Test
    void should_fanoutMarketClosed_when_stockTransitionsFromOpenToClosed() {
        MarketSessionTracker tracker = freshTracker();
        seedTracker(tracker, MarketSession.OPEN);
        resolverReturnsExcept(SessionMarket.STOCK, MarketSession.CLOSED, MarketSession.OPEN);
        when(fanoutService.fanout(anyString(), any(), any())).thenReturn(new FanoutResult(0, 0));

        scheduler(tracker).scanTransitions();

        ArgumentCaptor<String> labelCaptor = ArgumentCaptor.forClass(String.class);
        verify(fanoutService, times(1)).fanout(labelCaptor.capture(), any(), any());
        assertThat(labelCaptor.getValue()).contains("stock").contains("closed");
    }

    @Test
    void should_skipFanout_when_trackerEmptyForMarket() {
        MarketSessionTracker tracker = freshTracker();
        when(resolver.resolve(any(), any())).thenReturn(Optional.of(MarketSession.OPEN));

        scheduler(tracker).scanTransitions();

        verify(fanoutService, never()).fanout(anyString(), any(), any());
    }

    @Test
    void should_routeOpenedPageFetcher_through_findMarketOpenedSubscribed() {
        MarketSessionTracker tracker = freshTracker();
        seedTracker(tracker, MarketSession.CLOSED);
        resolverReturnsExcept(SessionMarket.STOCK, MarketSession.OPEN, MarketSession.CLOSED);
        when(fanoutService.fanout(anyString(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Function<Pageable, Page<NotificationPreference>> fetcher = inv.getArgument(1);
            fetcher.apply(Pageable.unpaged());
            return new FanoutResult(0, 0);
        });

        scheduler(tracker).scanTransitions();

        verify(preferences, times(1)).findMarketOpenedSubscribed(eq("STOCK"), any());
        verify(preferences, never()).findMarketClosedSubscribed(any(), any());
    }

    @Test
    void should_routeClosedPageFetcher_through_findMarketClosedSubscribed() {
        MarketSessionTracker tracker = freshTracker();
        seedTracker(tracker, MarketSession.OPEN);
        resolverReturnsExcept(SessionMarket.STOCK, MarketSession.CLOSED, MarketSession.OPEN);
        when(fanoutService.fanout(anyString(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Function<Pageable, Page<NotificationPreference>> fetcher = inv.getArgument(1);
            fetcher.apply(Pageable.unpaged());
            return new FanoutResult(0, 0);
        });

        scheduler(tracker).scanTransitions();

        verify(preferences, times(1)).findMarketClosedSubscribed(eq("STOCK"), any());
        verify(preferences, never()).findMarketOpenedSubscribed(any(), any());
    }

    @Test
    void should_supplyPayloadOfMatchingType_when_payloadFactoryInvoked() {
        MarketSessionTracker tracker = freshTracker();
        seedTracker(tracker, MarketSession.CLOSED);
        resolverReturnsExcept(SessionMarket.STOCK, MarketSession.OPEN, MarketSession.CLOSED);
        when(fanoutService.fanout(anyString(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Function<NotificationPreference, Optional<NotificationPayload>> factory = inv.getArgument(2);
            Optional<NotificationPayload> p = factory.apply(NotificationPreference.builder().userSub("u").build());
            assertThat(p).isPresent();
            assertThat(p.get().type().name()).isEqualTo("MARKET_OPENED");
            return new FanoutResult(0, 0);
        });

        scheduler(tracker).scanTransitions();
    }
}
