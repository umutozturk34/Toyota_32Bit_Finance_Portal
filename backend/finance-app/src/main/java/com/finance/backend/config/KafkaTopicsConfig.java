package com.finance.backend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

@Configuration
public class KafkaTopicsConfig {

    public static final String MARKET_UPDATED_TOPIC = "market.updated";
    public static final String USER_PREFERENCES_UPDATED_TOPIC = "user.preferences.updated";

    @Bean
    public NewTopic marketUpdatedTopic() {
        return TopicBuilder.name(MARKET_UPDATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(24 * 60 * 60 * 1000L))
                .build();
    }

    @Bean
    public NewTopic userPreferencesUpdatedTopic() {
        return TopicBuilder.name(USER_PREFERENCES_UPDATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .configs(Map.of(
                        "cleanup.policy", "compact",
                        "min.cleanable.dirty.ratio", "0.1"
                ))
                .build();
    }
}
