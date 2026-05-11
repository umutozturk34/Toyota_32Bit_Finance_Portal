package com.finance.app.event;

import com.finance.common.event.KafkaTopicsProperties;
import com.finance.common.event.MarketUpdatedEvent;
import com.finance.common.event.NewsPublishedEvent;
import com.finance.common.event.PortfolioUpdatedEvent;
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
class KafkaEventAdapterTest {

    private static final KafkaTopicsProperties TOPICS = new KafkaTopicsProperties(
            "market.updated",
            "news.published",
            "portfolio.updated",
            "user.email-change.code-requested",
            "mail.dispatch",
            "user.status.changed");

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaEventAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new KafkaEventAdapter(kafkaTemplate, TOPICS);
        SendResult<String, Object> result = new SendResult<>(null,
                new RecordMetadata(new TopicPartition("test", 0), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
    }

    @Test
    void should_routeMarketEventToMarketTopic_keyedByMarketType() {
        MarketUpdatedEvent event = MarketUpdatedEvent.of(MarketType.STOCK, "scheduler");

        adapter.publish(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), payload.capture());
        assertThat(topic.getValue()).isEqualTo(TOPICS.marketUpdated());
        assertThat(key.getValue()).isEqualTo("STOCK");
        assertThat(payload.getValue()).isSameAs(event);
    }

    @Test
    void should_routeNewsEventToNewsTopic_keyedByEventId() {
        NewsPublishedEvent event = NewsPublishedEvent.of("scheduled-news-morning");

        adapter.publish(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), any());
        assertThat(topic.getValue()).isEqualTo(TOPICS.newsPublished());
        assertThat(key.getValue()).isEqualTo(event.eventId());
    }

    @Test
    void should_routePortfolioEventToPortfolioTopic_keyedByEventId() {
        PortfolioUpdatedEvent event = PortfolioUpdatedEvent.of("daily-snapshot");

        adapter.publish(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), any());
        assertThat(topic.getValue()).isEqualTo(TOPICS.portfolioUpdated());
        assertThat(key.getValue()).isEqualTo(event.eventId());
    }
}
