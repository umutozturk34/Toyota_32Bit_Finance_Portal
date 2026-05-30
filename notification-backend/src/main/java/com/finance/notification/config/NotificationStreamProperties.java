package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** SSE stream tuning: emitter timeout and the registry's per-user TTL and capacity. */
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
