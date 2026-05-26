package com.finance.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.analytics")
public record AnalyticsProperties(
        Scenario scenario,
        Beater beater
) {

    public record Scenario(int partialThresholdDays) {}

    public record Beater(int cacheTtlHours) {}

    public AnalyticsProperties {
        if (scenario == null) scenario = new Scenario(30);
        if (beater == null) beater = new Beater(24);
    }
}
