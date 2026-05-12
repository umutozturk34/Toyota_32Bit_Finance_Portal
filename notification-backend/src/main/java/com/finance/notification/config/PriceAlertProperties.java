package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.alert")
public record PriceAlertProperties(
        int maxPerUser
) {}
