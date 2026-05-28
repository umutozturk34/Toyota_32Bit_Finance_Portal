package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Price-alert limits: maximum active alerts a single user may create (0 disables the cap). */
@ConfigurationProperties("notification.alert")
public record PriceAlertProperties(
        int maxPerUser
) {}
