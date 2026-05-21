package com.finance.common.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.kafka.topics")
public record KafkaTopicsProperties(
        String marketUpdated,
        String newsPublished,
        String portfolioUpdated,
        String macroIndicatorsUpdated,
        String userEmailChangeCode,
        String mailDispatch
) {
}
