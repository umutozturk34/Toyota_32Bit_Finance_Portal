package com.finance.notification.broadcast.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
