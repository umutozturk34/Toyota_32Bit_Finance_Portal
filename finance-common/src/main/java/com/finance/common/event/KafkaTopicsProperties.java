package com.finance.common.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externally configured Kafka topic names ({@code app.kafka.topics}); resolved per
 * {@link EventTopic} so producers/consumers never hard-code topic strings.
 */
@ConfigurationProperties("app.kafka.topics")
public record KafkaTopicsProperties(
        String marketUpdated,
        String newsPublished,
        String portfolioUpdated,
        String macroIndicatorsUpdated,
        String userEmailChangeCode,
        String userRegistered,
        String mailDispatch
) {
}
