package com.finance.common.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Defaults used when auto-creating Kafka topics ({@code app.kafka.admin}): partition counts
 * (standard and low-volume), replication factor and retention. Missing values fall back to safe
 * single-node defaults (3 partitions, 1 low-volume, 1 replica, 24h retention).
 */
@ConfigurationProperties("app.kafka.admin")
public record KafkaAdminProperties(
        Integer defaultPartitions,
        Integer lowVolumePartitions,
        Integer defaultReplicas,
        Long defaultRetentionMs
) {

    public KafkaAdminProperties {
        if (defaultPartitions == null) defaultPartitions = 3;
        if (lowVolumePartitions == null) lowVolumePartitions = 1;
        if (defaultReplicas == null) defaultReplicas = 1;
        if (defaultRetentionMs == null) defaultRetentionMs = 24L * 60L * 60L * 1000L;
    }
}
