package com.finance.notification.config;

import com.finance.common.event.KafkaAdminProperties;
import com.finance.common.event.KafkaTopicsProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics this service owns (mail dispatch, macro-indicators, email-change code)
 * so they are auto-created with the cluster's default partitions/replicas/retention.
 */
@Configuration
public class NotificationKafkaTopicsConfig {

    private final KafkaTopicsProperties topics;
    private final KafkaAdminProperties admin;

    public NotificationKafkaTopicsConfig(KafkaTopicsProperties topics, KafkaAdminProperties admin) {
        this.topics = topics;
        this.admin = admin;
    }

    @Bean
    public NewTopic mailDispatchTopic() {
        return TopicBuilder.name(topics.mailDispatch())
                .partitions(admin.defaultPartitions())
                .replicas(admin.defaultReplicas())
                .config("retention.ms", String.valueOf(admin.defaultRetentionMs()))
                .build();
    }

    @Bean
    public NewTopic macroIndicatorsUpdatedTopic() {
        return TopicBuilder.name(topics.macroIndicatorsUpdated())
                .partitions(admin.defaultPartitions())
                .replicas(admin.defaultReplicas())
                .config("retention.ms", String.valueOf(admin.defaultRetentionMs()))
                .build();
    }

    @Bean
    public NewTopic userEmailChangeCodeTopic() {
        return TopicBuilder.name(topics.userEmailChangeCode())
                .partitions(admin.defaultPartitions())
                .replicas(admin.defaultReplicas())
                .config("retention.ms", String.valueOf(admin.defaultRetentionMs()))
                .build();
    }
}
