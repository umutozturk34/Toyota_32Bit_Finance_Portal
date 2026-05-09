package com.finance.app.event;
import com.finance.common.event.KafkaTopics;

import com.finance.market.core.service.MarketSnapshotProcessor;

import com.finance.common.event.KafkaTopics;
import com.finance.common.event.UserPreferencesUpdatedEvent;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaUserPreferenceEventAdapterTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaUserPreferenceEventAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new KafkaUserPreferenceEventAdapter(kafkaTemplate);
        SendResult<String, Object> result = new SendResult<>(null,
                new RecordMetadata(new TopicPartition("test", 0), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
    }

    @Test
    void should_partitionByUserSub_when_publishingUserPreferencesEvent() {
        UserPreferencesUpdatedEvent event = new UserPreferencesUpdatedEvent(
                UUID.randomUUID().toString(),
                "kc-user-123",
                OffsetDateTime.now(),
                "DARK", "tr", "Europe/Istanbul", "1M", "DAILY", true);

        adapter.publishUserPreferencesUpdated(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), payload.capture());

        assertThat(topic.getValue()).isEqualTo(KafkaTopics.USER_PREFERENCES_UPDATED);
        assertThat(key.getValue()).isEqualTo("kc-user-123");
        assertThat(payload.getValue()).isSameAs(event);
    }
}
