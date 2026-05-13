package com.finance.notification.core.dispatch;

import com.finance.common.event.KafkaTopicsProperties;
import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.mail.EmailOutboxRepository;
import com.finance.notification.core.mapper.NotificationMapper;
import com.finance.notification.core.model.Notification;
import com.finance.notification.core.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPersisterTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private EmailOutboxRepository emailOutboxRepository;
    @Mock private NotificationStreamRegistry streamRegistry;
    @Mock private NotificationMapper notificationMapper;
    @SuppressWarnings("unchecked")
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private NotificationPersister persister;
    private Executor inline;

    @BeforeEach
    void setUp() {
        KafkaTopicsProperties topics = new KafkaTopicsProperties(
                "market", "news", "portfolio", "user", "mail.dispatch");
        inline = Runnable::run;
        persister = new NotificationPersister(notificationRepository, emailOutboxRepository,
                streamRegistry, notificationMapper, inline, kafkaTemplate, topics);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private Notification notification(String userSub) {
        return Notification.builder().userSub(userSub).build();
    }

    private EmailOutbox outbox(long id) {
        return EmailOutbox.builder().id(id).build();
    }

    private void runAfterCommit() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
    }

    @Test
    void persistBatch_noOp_whenBatchEmpty() {
        persister.persistBatch(List.of());

        verify(notificationRepository, never()).saveAll(any());
        verify(emailOutboxRepository, never()).saveAll(any());
    }

    @Test
    void persistBatch_savesInAppOnly_whenNoOutboxEntries() {
        Notification n = notification("user-1");
        Prepared prep = new Prepared("user-1", n, null);
        when(notificationRepository.saveAll(any())).thenReturn(List.of(n));

        persister.persistBatch(List.of(prep));

        verify(notificationRepository).saveAll(any());
        verify(emailOutboxRepository, never()).saveAll(any());
        verify(streamRegistry).publish(eq("user-1"), any());
    }

    @Test
    void persistBatch_marksOutboxRelayed_andSchedulesKafkaPublishAfterCommit() {
        EmailOutbox out = outbox(101L);
        Prepared prep = new Prepared("user-1", null, out);
        when(emailOutboxRepository.saveAll(any())).thenReturn(List.of(out));
        TransactionSynchronizationManager.initSynchronization();

        persister.persistBatch(List.of(prep));
        runAfterCommit();

        verify(emailOutboxRepository).saveAll(any());
        verify(kafkaTemplate).send(eq("mail.dispatch"), eq("101"), any());
    }

    @Test
    void persistBatch_publishesKafkaImmediately_whenNoSynchronizationActive() {
        EmailOutbox out = outbox(101L);
        Prepared prep = new Prepared("user-1", null, out);
        when(emailOutboxRepository.saveAll(any())).thenReturn(List.of(out));

        persister.persistBatch(List.of(prep));

        verify(kafkaTemplate).send(eq("mail.dispatch"), eq("101"), any());
    }

    @Test
    void persistBatch_persistsBothInAppAndOutbox_whenBothPresent() {
        Notification n = notification("user-1");
        EmailOutbox out = outbox(99L);
        Prepared prep = new Prepared("user-1", n, out);
        when(notificationRepository.saveAll(any())).thenReturn(List.of(n));
        when(emailOutboxRepository.saveAll(any())).thenReturn(List.of(out));

        persister.persistBatch(List.of(prep));

        verify(notificationRepository).saveAll(any());
        verify(emailOutboxRepository).saveAll(any());
        verify(streamRegistry).publish(eq("user-1"), any());
        verify(kafkaTemplate).send(eq("mail.dispatch"), eq("99"), any());
    }

    @Test
    void persistBatch_propagatesDataAccessException_fromRepository() {
        Notification n = notification("user-1");
        Prepared prep = new Prepared("user-1", n, null);
        when(notificationRepository.saveAll(any()))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertThatThrownBy(() -> persister.persistBatch(List.of(prep)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
