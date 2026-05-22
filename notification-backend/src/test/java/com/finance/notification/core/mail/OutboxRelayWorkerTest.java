package com.finance.notification.core.mail;

import com.finance.common.event.KafkaTopicsProperties;
import com.finance.notification.config.NotificationOutboxProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayWorkerTest {

    @Mock private EmailOutboxRepository repository;
    @SuppressWarnings("unchecked")
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private JdbcTemplate jdbcTemplate;

    private OutboxRelayWorker worker;

    @BeforeEach
    void setUp() {
        NotificationOutboxProperties props = new NotificationOutboxProperties(
                10, 3, List.of(Duration.ofSeconds(10)),
                Duration.ofMinutes(5), Duration.ofDays(7),
                Duration.ofSeconds(30), Duration.ofMinutes(1),
                "0 0 * * *");
        KafkaTopicsProperties topics = new KafkaTopicsProperties(
                "market", "news", "portfolio", "macro", "user", "mail.dispatch");
        worker = new OutboxRelayWorker(repository, kafkaTemplate, jdbcTemplate, props, topics);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void relayPending_skips_whenBatchEmpty() {
        when(repository.findPendingForProcessing(any(PageRequest.class))).thenReturn(List.of());

        worker.relayPending();

        verify(repository, never()).saveAll(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void relayPending_savesBatchAndPublishesAfterCommit_whenRowsAvailable() {
        EmailOutbox row1 = EmailOutbox.builder().id(1L).status(EmailOutbox.Status.PENDING).build();
        EmailOutbox row2 = EmailOutbox.builder().id(2L).status(EmailOutbox.Status.PENDING).build();
        when(repository.findPendingForProcessing(any(PageRequest.class)))
                .thenReturn(List.of(row1, row2));

        worker.relayPending();
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }

        verify(repository).saveAll(List.of(row1, row2));
        verify(kafkaTemplate).send(eq("mail.dispatch"), eq("1"), any());
        verify(kafkaTemplate).send(eq("mail.dispatch"), eq("2"), any());
    }

    @Test
    void reclaimStaleRelayed_runsUpdate_andLogsWhenRowsAffected() {
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(3);

        worker.reclaimStaleRelayed();

        verify(jdbcTemplate).update(anyString(), any(Timestamp.class));
    }

    @Test
    void reclaimStaleRelayed_isQuiet_whenNoRowsAffected() {
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(0);

        worker.reclaimStaleRelayed();

        verify(jdbcTemplate).update(anyString(), any(Timestamp.class));
    }
}
