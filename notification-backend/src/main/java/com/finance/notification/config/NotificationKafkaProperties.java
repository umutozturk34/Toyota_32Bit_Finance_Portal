package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka consumer retry tuning (interval and max attempts) before routing to the DLQ. */
@ConfigurationProperties("notification.kafka")
public record NotificationKafkaProperties(
        Consumer consumer
) {

    /** Listener retry policy: the back-off interval between redeliveries and the attempt count after which a record is sent to the DLQ. */
    public record Consumer(
            long retryIntervalMs,
            long retryMaxAttempts
    ) {}
}
