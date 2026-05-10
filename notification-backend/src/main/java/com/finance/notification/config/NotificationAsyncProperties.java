package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.async.mail")
public record NotificationAsyncProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        int awaitTerminationSeconds,
        String threadNamePrefix
) {
}
