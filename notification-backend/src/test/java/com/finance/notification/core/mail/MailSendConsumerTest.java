package com.finance.notification.core.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.notification.config.NotificationOutboxProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailSendConsumerTest {

    @Mock private EmailOutboxRepository repository;
    @Mock private MailSender mailSender;
    @Mock private PlatformTransactionManager txManager;
    @Mock private Acknowledgment ack;

    private MailSendConsumer consumer;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        NotificationOutboxProperties props = new NotificationOutboxProperties(
                10,
                3, List.of(Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5)),
                Duration.ofMinutes(5), Duration.ofDays(7),
                Duration.ofSeconds(30), Duration.ofMinutes(1),
                "0 0 * * *");
        consumer = new MailSendConsumer(repository, mailSender, new ObjectMapper(),
                props, txManager, new SimpleMeterRegistry());
    }

    private EmailOutbox row(long id) {
        EmailOutbox row = new EmailOutbox();
        row.setId(id);
        row.setRecipientEmail("user@example.com");
        row.setSubject("Subject");
        row.setTemplateName("template");
        row.setModel(new ObjectMapper().valueToTree(java.util.Map.of("k", "v")));
        row.setTheme("dark");
        row.setLocale("tr");
        row.setStatus(EmailOutbox.Status.RELAYED);
        return row;
    }

    @Test
    void onDispatch_skips_whenRowAlreadyClaimed() {
        when(repository.claimForProcessing(42L)).thenReturn(0);

        consumer.onDispatch(new MailDispatchEvent(42L), ack);

        verify(repository, never()).findById(anyLong());
        verify(mailSender, never()).sendBlocking(anyString(), anyString(), anyString(), any(), anyString(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onDispatch_marksRowSent_andCommits_whenMailSenderSucceeds() {
        EmailOutbox row = row(42L);
        when(repository.claimForProcessing(42L)).thenReturn(1);
        when(repository.findById(42L)).thenReturn(Optional.of(row));

        consumer.onDispatch(new MailDispatchEvent(42L), ack);

        verify(mailSender).sendBlocking(anyString(), anyString(), anyString(), any(), anyString(), any());
        verify(repository).save(row);
        verify(ack).acknowledge();
    }

    @Test
    void onDispatch_movesRowToFailed_whenAttemptsExceedMax() {
        EmailOutbox row = row(42L);
        row.setAttempts(2);
        when(repository.claimForProcessing(42L)).thenReturn(1);
        when(repository.findById(42L)).thenReturn(Optional.of(row));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down"))
                .when(mailSender).sendBlocking(anyString(), anyString(), anyString(), any(), anyString(), any());

        consumer.onDispatch(new MailDispatchEvent(42L), ack);

        verify(repository).save(row);
        verify(ack).acknowledge();
    }

    @Test
    void onDispatch_marksRetry_whenAttemptBelowMax() {
        EmailOutbox row = row(42L);
        row.setAttempts(0);
        when(repository.claimForProcessing(42L)).thenReturn(1);
        when(repository.findById(42L)).thenReturn(Optional.of(row));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp flaky"))
                .when(mailSender).sendBlocking(anyString(), anyString(), anyString(), any(), anyString(), any());

        consumer.onDispatch(new MailDispatchEvent(42L), ack);

        verify(repository).save(row);
        verify(ack).acknowledge();
    }

    @Test
    void onDispatch_clampsBackoffIndex_whenMaxAttemptsExceedsBackoffSchedule() {
        // Arrange: max-attempts (3) exceeds backoffs size (1); attempt 2 fails into the else
        // branch with index 1, which is past the schedule and must be clamped, not throw.
        NotificationOutboxProperties props = new NotificationOutboxProperties(
                10,
                3, List.of(Duration.ofMinutes(1)),
                Duration.ofMinutes(5), Duration.ofDays(7),
                Duration.ofSeconds(30), Duration.ofMinutes(1),
                "0 0 * * *");
        MailSendConsumer clampConsumer = new MailSendConsumer(repository, mailSender, new ObjectMapper(),
                props, txManager, new SimpleMeterRegistry());
        EmailOutbox row = row(42L);
        row.setAttempts(1);
        when(repository.claimForProcessing(42L)).thenReturn(1);
        when(repository.findById(42L)).thenReturn(Optional.of(row));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp flaky"))
                .when(mailSender).sendBlocking(anyString(), anyString(), anyString(), any(), anyString(), any());

        // Act
        clampConsumer.onDispatch(new MailDispatchEvent(42L), ack);

        // Assert
        org.junit.jupiter.api.Assertions.assertEquals(EmailOutbox.Status.PENDING, row.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(row.getNextAttemptAt());
        verify(repository).save(row);
        verify(ack).acknowledge();
    }

    @Test
    void onDispatch_swallowsOptimisticLockingOnSentSave() {
        EmailOutbox row = row(42L);
        when(repository.claimForProcessing(42L)).thenReturn(1);
        when(repository.findById(42L)).thenReturn(Optional.of(row));
        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("lost"))
                .when(repository).save(row);

        consumer.onDispatch(new MailDispatchEvent(42L), ack);

        verify(ack).acknowledge();
    }

    @Test
    void onDispatch_swallowsOptimisticLockingFromMailSender() {
        EmailOutbox row = row(42L);
        when(repository.claimForProcessing(42L)).thenReturn(1);
        when(repository.findById(42L)).thenReturn(Optional.of(row));
        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("stale"))
                .when(mailSender).sendBlocking(anyString(), anyString(), anyString(), any(), anyString(), any());

        consumer.onDispatch(new MailDispatchEvent(42L), ack);

        verify(ack).acknowledge();
    }

}
