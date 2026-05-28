package com.finance.notification.broadcast.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for broadcasts: dispatch batch size and the hard recipient cap above which a broadcast is
 * refused. Non-positive values fall back to sane defaults.
 */
@ConfigurationProperties(prefix = "notification.broadcast")
public record BroadcastProperties(
        int batchSize,
        int maxRecipients
) {

    public BroadcastProperties {
        if (batchSize <= 0) batchSize = 100;
        if (maxRecipients <= 0) maxRecipients = 100_000;
    }
}
