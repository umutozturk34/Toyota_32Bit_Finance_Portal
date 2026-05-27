package com.finance.app.event;

import com.finance.common.event.DomainEvent;
import com.finance.common.event.KafkaTopicsProperties;
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
        String topic = event.topic().resolve(topics);
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
}
