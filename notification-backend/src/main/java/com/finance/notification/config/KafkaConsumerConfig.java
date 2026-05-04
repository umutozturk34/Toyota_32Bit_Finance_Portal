package com.finance.notification.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Log4j2
@Configuration
public class KafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MS = 2000L;
    private static final long RETRY_MAX_ATTEMPTS = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.warn("Routing to DLQ topic={}.dlq partition={} key={}",
                            record.topic(), record.partition(), record.key());
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".dlq", record.partition());
                });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRY_MAX_ATTEMPTS));
    }
}
