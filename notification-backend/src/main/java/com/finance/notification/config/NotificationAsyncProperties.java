package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thread-pool sizing and naming for the SSE push executor. */
@ConfigurationProperties("notification.async.sse")
public record NotificationAsyncProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        int awaitTerminationSeconds,
        String threadNamePrefix
) {
}
