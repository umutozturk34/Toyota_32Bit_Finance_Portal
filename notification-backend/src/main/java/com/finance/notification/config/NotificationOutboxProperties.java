package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("notification.email.outbox")
public record NotificationOutboxProperties(
        int batchSize,
        int maxAttempts,
        List<Duration> backoffs,
        Duration reclaimAfter,
        Duration sentRetention,
        Duration pollDelay,
        Duration reclaimDelay,
        String cleanupCron
) {
}
