package com.finance.notification.market;

import com.finance.notification.market.session.SessionMarket;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.dispatch.payload.MarketDataUpdatedPayload;
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

    private final NotificationFanoutService fanoutService;
    private final NotificationPreferenceRepository preferences;
    private final Cache<String, Boolean> processedEventIds;

    public MarketDataUpdateListener(NotificationFanoutService fanoutService,
                                    NotificationPreferenceRepository preferences,
                                    @Qualifier("dataUpdatedProcessedEventIds") Cache<String, Boolean> processedEventIds) {
        this.fanoutService = fanoutService;
        this.preferences = preferences;
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.market-updated}",
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
        MarketDataUpdatedPayload payload = new MarketDataUpdatedPayload(market.name(), event.source());
        FanoutResult result = fanoutService.fanout(
                "market.updated",
                page -> preferences.findMarketDataSubscribed(market.name(), page),
                pref -> Optional.of(payload));
        log.info("market.updated marketType={} source={} dispatched={} failed={}",
                event.marketType(), event.source(), result.dispatched(), result.failed());
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
