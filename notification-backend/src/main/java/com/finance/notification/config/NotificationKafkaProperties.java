package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.kafka")
public record NotificationKafkaProperties(
        Consumer consumer
) {

    public record Consumer(
            long retryIntervalMs,
            long retryMaxAttempts
    ) {}
}
