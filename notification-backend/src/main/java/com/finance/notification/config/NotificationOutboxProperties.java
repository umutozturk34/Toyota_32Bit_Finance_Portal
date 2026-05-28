package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Email-outbox processing tuning: relay batch size, max send attempts and the per-attempt backoff
 * schedule, plus the stale-reclaim window, SENT retention, and poll/reclaim/cleanup cadences.
 */
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
