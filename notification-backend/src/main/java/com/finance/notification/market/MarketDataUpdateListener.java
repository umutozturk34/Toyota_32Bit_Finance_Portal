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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Subscribes to {@code market.updated} on a dedicated consumer group and
 * dispatches {@code MARKET_DATA_UPDATED} notifications for every cron-driven
 * refresh. Open / close transitions live on a separate minute-tick scheduler
 * ({@link MarketSessionScheduler}) so they fire at the actual boundary instead
 * of the next data-refresh cron.
 *
 * <p>Idempotency: per-eventId Caffeine cache prevents duplicate fan-out when
 * Kafka redelivers (consumer-group offset reset, replay, retry, etc.). Without
 * this, a single restart with offset-reset earliest would email every persisted
 * event.
 */
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
        try {
            handleSafely(event);
        } catch (RuntimeException ex) {
            log.error("Market data-updated listener failed marketType={} eventId={}: {}",
                    event.marketType(), event.eventId(), ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
    }

    private void handleSafely(MarketUpdatedEvent event) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate market-updated event {} marketType={}, skip dispatch",
                    event.eventId(), event.marketType());
            return;
        }
        Optional<SessionMarket> mapped = MarketTypeMapper.fromMarketType(event.marketType());
        if (mapped.isEmpty()) {
            log.debug("Skipping data-updated dispatch — no SessionMarket for {}", event.marketType());
            processedEventIds.put(event.eventId(), Boolean.TRUE);
            return;
        }
        SessionMarket market = mapped.get();
        for (NotificationPreference pref : preferences.findAll()) {
            if (!isMarketSelected(pref, market)) continue;
            MarketDataUpdatedPayload payload = new MarketDataUpdatedPayload(
                    market.name(), market.displayLabel(), event.source());
            dispatcher.dispatch(NotificationRequest.of(pref.getUserSub(), payload));
        }
        processedEventIds.put(event.eventId(), Boolean.TRUE);
    }

    private boolean isMarketSelected(NotificationPreference pref, SessionMarket market) {
        String selection = pref.getMarketSessionMarkets();
        if (selection == null || selection.isBlank()) return false;
        Set<String> selected = new HashSet<>(Arrays.asList(selection.split(",")));
        return selected.contains(market.name());
    }
}
