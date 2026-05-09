package com.finance.app.config;

import com.finance.common.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic marketUpdatedTopic() {
        return TopicBuilder.name(KafkaTopics.MARKET_UPDATED)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(24 * 60 * 60 * 1000L))
                .build();
    }

    @Bean
    public NewTopic userPreferencesUpdatedTopic() {
        return TopicBuilder.name(KafkaTopics.USER_PREFERENCES_UPDATED)
                .partitions(3)
                .replicas(1)
                .configs(Map.of(
                        "cleanup.policy", "compact",
                        "min.cleanable.dirty.ratio", "0.1"
                ))
                .build();
    }

    @Bean
    public NewTopic newsPublishedTopic() {
        return TopicBuilder.name(KafkaTopics.NEWS_PUBLISHED)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", String.valueOf(24 * 60 * 60 * 1000L))
                .build();
    }

    @Bean
    public NewTopic portfolioUpdatedTopic() {
        return TopicBuilder.name(KafkaTopics.PORTFOLIO_UPDATED)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(24 * 60 * 60 * 1000L))
                .build();
    }
}
