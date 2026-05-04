package com.finance.notification.listener;

import com.finance.backend.event.MarketUpdatedEvent;
import com.finance.backend.model.MarketType;
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

    private MarketUpdateEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new MarketUpdateEventListener(Caffeine.newBuilder().build());
    }

    @Test
    void should_acknowledge_when_marketEventReceived() {
        MarketUpdatedEvent event = MarketUpdatedEvent.of(MarketType.STOCK, "scheduler");

        listener.onMarketUpdated(event, ack);

        verify(ack).acknowledge();
    }

    @Test
    void should_acknowledgeBoth_when_sameEventArrivesTwice() {
        MarketUpdatedEvent event = MarketUpdatedEvent.of(MarketType.CRYPTO, "scheduler");

        listener.onMarketUpdated(event, ack);
        listener.onMarketUpdated(event, ack);

        verify(ack, times(2)).acknowledge();
    }
}
