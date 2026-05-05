package com.finance.notification.listener;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.model.MarketType;
import com.finance.notification.alert.service.PriceAlertEvaluator;
import com.finance.notification.watchlist.service.WatchlistEvaluator;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketUpdateEventListenerTest {

    @Mock private Acknowledgment ack;
    @Mock private PriceAlertEvaluator priceAlertEvaluator;
    @Mock private WatchlistEvaluator watchlistEvaluator;

    private MarketUpdateEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new MarketUpdateEventListener(Caffeine.newBuilder().build(),
                priceAlertEvaluator, watchlistEvaluator);
    }

    @Test
    void should_evaluateBothEvaluators_when_marketEventReceived() {
        MarketUpdatedEvent event = MarketUpdatedEvent.of(MarketType.CRYPTO, "scheduler");

        listener.onMarketUpdated(event, ack);

        verify(priceAlertEvaluator).evaluate(MarketType.CRYPTO);
        verify(watchlistEvaluator).evaluate(MarketType.CRYPTO);
        verify(ack).acknowledge();
    }

    @Test
    void should_skipEvaluators_when_duplicateEventArrives() {
        MarketUpdatedEvent event = MarketUpdatedEvent.of(MarketType.STOCK, "scheduler");

        listener.onMarketUpdated(event, ack);
        listener.onMarketUpdated(event, ack);

        verify(priceAlertEvaluator, times(1)).evaluate(MarketType.STOCK);
        verify(watchlistEvaluator, times(1)).evaluate(MarketType.STOCK);
        verify(ack, times(2)).acknowledge();
    }
}
