package com.finance.app.event;

import com.finance.common.event.KafkaTopics;
import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.event.MarketUpdateEventPort;
import com.finance.common.model.MarketType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMarketEventAdapter implements MarketUpdateEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishMarketUpdated(MarketType marketType, String source) {
        MarketUpdatedEvent event = MarketUpdatedEvent.of(marketType, source);
        kafkaTemplate.send(KafkaTopics.MARKET_UPDATED, marketType.name(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish market.updated for {}: {}", marketType, ex.getMessage());
                    } else {
                        log.debug("Published market.updated for {}", marketType);
                    }
                });
    }
}
