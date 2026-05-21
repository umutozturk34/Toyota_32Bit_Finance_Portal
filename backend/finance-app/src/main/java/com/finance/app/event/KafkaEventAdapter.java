package com.finance.app.event;

import com.finance.common.event.DomainEvent;
import com.finance.common.event.EmailChangeCodeRequestedEvent;
import com.finance.common.event.KafkaTopicsProperties;
import com.finance.common.event.MacroIndicatorsUpdatedEvent;
import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.event.NewsPublishedEvent;
import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.shared.event.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaEventAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    @Override
    public void publish(DomainEvent event) {
        String topic = resolveTopic(event);
        kafkaTemplate.send(topic, event.partitionKey(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} eventId={}: {}",
                                topic, event.eventId(), ex.getMessage());
                    } else {
                        log.debug("Published {} eventId={}", topic, event.eventId());
                    }
                });
    }

    private String resolveTopic(DomainEvent event) {
        return switch (event) {
            case MarketUpdatedEvent ignored -> topics.marketUpdated();
            case NewsPublishedEvent ignored -> topics.newsPublished();
            case PortfolioUpdatedEvent ignored -> topics.portfolioUpdated();
            case MacroIndicatorsUpdatedEvent ignored -> topics.macroIndicatorsUpdated();
            case EmailChangeCodeRequestedEvent ignored -> topics.userEmailChangeCode();
            default -> throw new IllegalArgumentException(
                    "Unknown event type for topic resolution: " + event.getClass().getName());
        };
    }
}
