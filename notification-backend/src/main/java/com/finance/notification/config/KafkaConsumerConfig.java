package com.finance.notification.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configures the shared Kafka error handler: fixed-interval retries, then routing the failed record
 * to a per-topic {@code <topic>.dlq} dead-letter topic so poison messages don't block the partition.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final NotificationKafkaProperties properties;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.warn("Routing to DLQ topic={}.dlq partition={} key={}",
                            record.topic(), record.partition(), record.key());
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".dlq", record.partition());
                });
        return new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.consumer().retryIntervalMs(), properties.consumer().retryMaxAttempts()));
    }
}
