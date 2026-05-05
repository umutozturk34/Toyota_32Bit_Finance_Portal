package com.finance.app.event;

import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.event.KafkaTopics;
import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.event.MarketUpdateEventPort;
import com.finance.common.model.MarketType;
import com.finance.common.service.AssetPricingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMarketEventAdapter implements MarketUpdateEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AssetPricingPort assetPricingPort;

    @Override
    public void publishMarketUpdated(MarketType marketType, String source) {
        Map<String, AssetSnapshot> latestSnapshots = assetPricingPort.getAllSnapshots(marketType);
        MarketUpdatedEvent event = MarketUpdatedEvent.of(marketType, source, latestSnapshots);
        kafkaTemplate.send(KafkaTopics.MARKET_UPDATED, marketType.name(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish market.updated for {}: {}", marketType, ex.getMessage());
                    } else {
                        log.debug("Published market.updated for {} with {} snapshots", marketType, latestSnapshots.size());
                    }
                });
    }
}
