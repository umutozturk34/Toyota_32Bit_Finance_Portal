package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.stream")
public record NotificationStreamProperties(
        long emitterTimeoutMs,
        Integer registryTtlMinutes,
        Integer registryMaxUsers
) {

    public NotificationStreamProperties {
        if (registryTtlMinutes == null) registryTtlMinutes = 30;
        if (registryMaxUsers == null) registryMaxUsers = 10_000;
    }
}
