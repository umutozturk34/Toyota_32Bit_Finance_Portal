package com.finance.notification.market;

import com.finance.common.event.KafkaTopics;
import com.finance.common.event.MarketUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.MarketDataUpdatedPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Log4j2
@Component
public class MarketDataUpdateListener {

    private static final String GROUP_ID = "notification-data-updated";

    private final NotificationDispatcher dispatcher;
    private final NotificationPreferenceRepository preferences;
    private final Cache<String, Boolean> processedEventIds;

    public MarketDataUpdateListener(NotificationDispatcher dispatcher,
                                    NotificationPreferenceRepository preferences,
                                    @Qualifier("dataUpdatedProcessedEventIds") Cache<String, Boolean> processedEventIds) {
        this.dispatcher = dispatcher;
        this.preferences = preferences;
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = KafkaTopics.MARKET_UPDATED,
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onMarketUpdated(MarketUpdatedEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate market-updated event {} marketType={}, skip dispatch",
                    event.eventId(), event.marketType());
            ack.acknowledge();
            return;
        }
        Optional<SessionMarket> mapped = MarketTypeMapper.fromMarketType(event.marketType());
        if (mapped.isEmpty()) {
            log.debug("Skipping data-updated dispatch — no SessionMarket for {}", event.marketType());
            processedEventIds.put(event.eventId(), Boolean.TRUE);
            ack.acknowledge();
            return;
        }
        SessionMarket market = mapped.get();
        for (NotificationPreference pref : preferences.findAll()) {
            if (!pref.subscribesToMarket(market)) continue;
            MarketDataUpdatedPayload payload = new MarketDataUpdatedPayload(
                    market.name(), market.displayLabel(), event.source());
            dispatcher.dispatch(NotificationRequest.of(pref.getUserSub(), payload));
        }
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
