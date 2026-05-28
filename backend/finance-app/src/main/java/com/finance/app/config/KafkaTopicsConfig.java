package com.finance.app.config;

import com.finance.common.event.KafkaAdminProperties;
import com.finance.common.event.KafkaTopicsProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics this monolith owns so they are auto-created with the configured partitions,
 * replicas and retention. Topic names and admin defaults come from shared configuration properties.
 */
@Configuration
public class KafkaTopicsConfig {

    private final KafkaTopicsProperties topics;
    private final KafkaAdminProperties admin;

    public KafkaTopicsConfig(KafkaTopicsProperties topics, KafkaAdminProperties admin) {
        this.topics = topics;
        this.admin = admin;
    }

    @Bean
    public NewTopic marketUpdatedTopic() {
        return TopicBuilder.name(topics.marketUpdated())
                .partitions(admin.defaultPartitions())
                .replicas(admin.defaultReplicas())
                .config("retention.ms", String.valueOf(admin.defaultRetentionMs()))
                .build();
    }

    @Bean
    public NewTopic newsPublishedTopic() {
        return TopicBuilder.name(topics.newsPublished())
                .partitions(admin.lowVolumePartitions())
                .replicas(admin.defaultReplicas())
                .config("retention.ms", String.valueOf(admin.defaultRetentionMs()))
                .build();
    }

    @Bean
    public NewTopic portfolioUpdatedTopic() {
        return TopicBuilder.name(topics.portfolioUpdated())
                .partitions(admin.defaultPartitions())
                .replicas(admin.defaultReplicas())
                .config("retention.ms", String.valueOf(admin.defaultRetentionMs()))
                .build();
    }

    @Bean
    public NewTopic userRegisteredTopic() {
        return TopicBuilder.name(topics.userRegistered())
                .partitions(admin.lowVolumePartitions())
                .replicas(admin.defaultReplicas())
                .config("retention.ms", String.valueOf(admin.defaultRetentionMs()))
                .build();
    }
}
