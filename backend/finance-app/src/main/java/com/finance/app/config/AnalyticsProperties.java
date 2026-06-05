package com.finance.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tuning for the analytics features under {@code app.analytics}, with sensible defaults if unset. */
@ConfigurationProperties("app.analytics")
public record AnalyticsProperties(
        Scenario scenario
) {

    /** {@code partialThresholdDays}: how far a series may fall short of the window before it's flagged partial. */
    public record Scenario(int partialThresholdDays) {}

    public AnalyticsProperties {
        if (scenario == null) scenario = new Scenario(30);
    }
}
