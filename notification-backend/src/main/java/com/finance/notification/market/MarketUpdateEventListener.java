package com.finance.notification.market;

import com.finance.common.event.MarketUpdatedEvent;
import com.finance.notification.alert.service.PriceAlertEvaluator;
import com.finance.notification.watchlist.service.WatchlistEvaluator;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that, on a market-data-updated event, triggers price-alert and watchlist evaluation
 * for the affected market. Deduplicates by event id; runs in its own consumer group so it processes
 * the same topic independently of the data-updated notification listener.
 */
@Log4j2
@Component
public class MarketUpdateEventListener {

    private final Cache<String, Boolean> processedEventIds;
    private final PriceAlertEvaluator priceAlertEvaluator;
    private final WatchlistEvaluator watchlistEvaluator;

    public MarketUpdateEventListener(@Qualifier("processedEventIds") Cache<String, Boolean> processedEventIds,
                                     PriceAlertEvaluator priceAlertEvaluator,
                                     WatchlistEvaluator watchlistEvaluator) {
        this.processedEventIds = processedEventIds;
        this.priceAlertEvaluator = priceAlertEvaluator;
        this.watchlistEvaluator = watchlistEvaluator;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.market-updated}",
            groupId = "${spring.kafka.consumer.group-id}-market"
    )
    public void onMarketUpdated(MarketUpdatedEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate event {} for market {}, skip", event.eventId(), event.marketType());
            ack.acknowledge();
            return;
        }
        log.info("Market updated event received: marketType={} source={} eventId={}",
                event.marketType(), event.source(), event.eventId());
        priceAlertEvaluator.evaluate(event.marketType());
        watchlistEvaluator.evaluate(event.marketType());
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
