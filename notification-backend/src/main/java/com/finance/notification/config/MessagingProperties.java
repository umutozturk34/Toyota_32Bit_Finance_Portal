package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.messaging")
public record MessagingProperties(
        int backlogMaxUnanswered,
        int cooldownSeconds,
        int duplicateWindowSeconds
) {}
