package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka consumer retry tuning (interval and max attempts) before routing to the DLQ. */
@ConfigurationProperties("notification.kafka")
public record NotificationKafkaProperties(
        Consumer consumer
) {

    public record Consumer(
            long retryIntervalMs,
            long retryMaxAttempts
    ) {}
}
