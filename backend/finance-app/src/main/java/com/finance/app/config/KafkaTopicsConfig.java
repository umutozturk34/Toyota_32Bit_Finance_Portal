package com.finance.app.config;

import com.finance.common.event.KafkaTopicsProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    private static final String RETENTION_24H_MS = String.valueOf(24 * 60 * 60 * 1000L);

    private final KafkaTopicsProperties topics;

    public KafkaTopicsConfig(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    @Bean
    public NewTopic marketUpdatedTopic() {
        return TopicBuilder.name(topics.marketUpdated())
                .partitions(3)
                .replicas(1)
                .config("retention.ms", RETENTION_24H_MS)
                .build();
    }

    @Bean
    public NewTopic newsPublishedTopic() {
        return TopicBuilder.name(topics.newsPublished())
                .partitions(1)
                .replicas(1)
                .config("retention.ms", RETENTION_24H_MS)
                .build();
    }

    @Bean
    public NewTopic portfolioUpdatedTopic() {
        return TopicBuilder.name(topics.portfolioUpdated())
                .partitions(3)
                .replicas(1)
                .config("retention.ms", RETENTION_24H_MS)
                .build();
    }
}
