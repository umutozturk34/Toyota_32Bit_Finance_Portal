package com.finance.app.event;
import com.finance.common.event.MarketUpdatedEvent;

import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.app.config.KafkaTopicsConfig;
import com.finance.common.model.MarketType;
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

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaMarketEventAdapterTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaMarketEventAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new KafkaMarketEventAdapter(kafkaTemplate);
        SendResult<String, Object> result = new SendResult<>(null,
                new RecordMetadata(new TopicPartition("test", 0), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
    }

    @Test
    void should_sendToMarketUpdatedTopic_when_publishingMarketEvent() {
        adapter.publishMarketUpdated(MarketType.STOCK, "scheduler");

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), payload.capture());

        assertThat(topic.getValue()).isEqualTo(KafkaTopicsConfig.MARKET_UPDATED_TOPIC);
        assertThat(key.getValue()).isEqualTo("STOCK");
        assertThat(payload.getValue()).isInstanceOf(MarketUpdatedEvent.class);
        MarketUpdatedEvent event = (MarketUpdatedEvent) payload.getValue();
        assertThat(event.marketType()).isEqualTo(MarketType.STOCK);
        assertThat(event.source()).isEqualTo("scheduler");
        assertThat(event.eventId()).isNotBlank();
    }

    @Test
    void should_partitionByMarketType_when_publishingMultipleEvents() {
        adapter.publishMarketUpdated(MarketType.CRYPTO, "scheduler");
        adapter.publishMarketUpdated(MarketType.FOREX, "admin");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2))
                .send(anyString(), keys.capture(), any());

        assertThat(keys.getAllValues()).containsExactly("CRYPTO", "FOREX");
    }
}
