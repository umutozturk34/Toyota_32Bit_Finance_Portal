package com.finance.common.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaAdminPropertiesTest {

    @Test
    void shouldExposeAllProvidedFields_whenAllValuesNonNull() {
        KafkaAdminProperties props = new KafkaAdminProperties(6, 2, 3, 10_000L);

        assertThat(props.defaultPartitions()).isEqualTo(6);
        assertThat(props.lowVolumePartitions()).isEqualTo(2);
        assertThat(props.defaultReplicas()).isEqualTo(3);
        assertThat(props.defaultRetentionMs()).isEqualTo(10_000L);
    }

    @Test
    void shouldFillDefaults_whenAllFieldsNull() {
        KafkaAdminProperties props = new KafkaAdminProperties(null, null, null, null);

        assertThat(props.defaultPartitions()).isEqualTo(3);
        assertThat(props.lowVolumePartitions()).isEqualTo(1);
        assertThat(props.defaultReplicas()).isEqualTo(1);
        assertThat(props.defaultRetentionMs()).isEqualTo(24L * 60L * 60L * 1000L);
    }

    @Test
    void shouldPreserveExplicitValues_whenOnlySomeFieldsNull() {
        KafkaAdminProperties props = new KafkaAdminProperties(12, null, 2, null);

        assertThat(props.defaultPartitions()).isEqualTo(12);
        assertThat(props.lowVolumePartitions()).isEqualTo(1);
        assertThat(props.defaultReplicas()).isEqualTo(2);
        assertThat(props.defaultRetentionMs()).isEqualTo(86_400_000L);
    }
}
