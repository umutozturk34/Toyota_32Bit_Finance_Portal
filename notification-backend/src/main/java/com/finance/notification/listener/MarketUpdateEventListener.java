package com.finance.notification.listener;

import com.finance.common.event.MarketUpdatedEvent;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class MarketUpdateEventListener {

    private final Cache<String, Boolean> processedEventIds;

    public MarketUpdateEventListener(@Qualifier("processedEventIds") Cache<String, Boolean> processedEventIds) {
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = "market.updated",
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
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
