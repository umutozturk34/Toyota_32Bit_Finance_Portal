package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Price-alert limits: the most active alerts a user may hold in total and on a single asset (0 disables a cap). */
@ConfigurationProperties("notification.alert")
public record PriceAlertProperties(
        int maxPerUser,
        int maxPerAsset
) {}
