package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.stream")
public record NotificationStreamProperties(
        long emitterTimeoutMs
) {}
