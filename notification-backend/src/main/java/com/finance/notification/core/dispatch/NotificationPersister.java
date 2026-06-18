package com.finance.notification.core.dispatch;

import com.finance.common.event.KafkaTopicsProperties;
import com.finance.notification.config.NotificationAsyncConfig;
import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.mail.EmailOutboxRepository;
import com.finance.notification.core.mail.MailDispatchEvent;
import com.finance.notification.core.mapper.NotificationMapper;
import com.finance.notification.core.model.Notification;
import com.finance.notification.core.repository.NotificationRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Persists prepared in-app notifications and email outbox rows in one transactional batch, then
 * triggers side effects only after the transaction commits: SSE push to connected clients (off the
 * dedicated executor) and a Kafka mail-dispatch event per outbox row. Outbox rows are marked
 * RELAYED at save time since the relay is fired post-commit.
 */
@Log4j2
@Service
public class NotificationPersister {

    private final NotificationRepository notificationRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final NotificationStreamRegistry streamRegistry;
    private final NotificationMapper notificationMapper;
    private final Executor sseExecutor;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String mailDispatchTopic;

    public NotificationPersister(NotificationRepository notificationRepository,
                                 EmailOutboxRepository emailOutboxRepository,
                                 NotificationStreamRegistry streamRegistry,
                                 NotificationMapper notificationMapper,
                                 @Qualifier(NotificationAsyncConfig.SSE_EXECUTOR) Executor sseExecutor,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 KafkaTopicsProperties topics) {
        this.notificationRepository = notificationRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.streamRegistry = streamRegistry;
        this.notificationMapper = notificationMapper;
        this.sseExecutor = sseExecutor;
        this.kafkaTemplate = kafkaTemplate;
        this.mailDispatchTopic = topics.mailDispatch();
    }

    // REQUIRES_NEW so each page commits in its own short transaction rather than joining the long
    // outer fanout tx; this also lets per-page SSE/mail post-commit hooks fire incrementally.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBatch(List<Prepared> batch) {
        if (batch.isEmpty()) return;

        List<Notification> notifs = new ArrayList<>(batch.size());
        List<EmailOutbox> outboxRows = new ArrayList<>(batch.size());
        for (Prepared p : batch) {
            if (p.inappEntity() != null) notifs.add(p.inappEntity());
            if (p.outboxRow() != null) outboxRows.add(p.outboxRow());
        }

        LocalDateTime now = LocalDateTime.now();
        for (EmailOutbox row : outboxRows) {
            row.setStatus(EmailOutbox.Status.RELAYED);
            row.setRelayedAt(now);
        }

        List<Notification> persisted;
        List<EmailOutbox> savedOutbox;
        try {
            persisted = notifs.isEmpty()
                    ? List.of()
                    : notificationRepository.saveAll(notifs);
            savedOutbox = outboxRows.isEmpty()
                    ? List.of()
                    : emailOutboxRepository.saveAll(outboxRows);
        } catch (DataAccessException ex) {
            log.error("Batch persist failed size={} inApp={} outbox={}: {}",
                    batch.size(), notifs.size(), outboxRows.size(), ex.getMessage());
            throw ex;
        }

        if (!persisted.isEmpty()) registerSsePushAfterCommit(persisted);
        if (!savedOutbox.isEmpty()) registerMailPublishAfterCommit(savedOutbox);
    }

    private void registerSsePushAfterCommit(List<Notification> persisted) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchAsync(persisted);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchAsync(persisted);
            }
        });
    }

    private void dispatchAsync(List<Notification> persisted) {
        for (Notification n : persisted) {
            sseExecutor.execute(() -> publishSse(n));
        }
    }

    private void publishSse(Notification persisted) {
        streamRegistry.publish(persisted.getUserSub(), notificationMapper.toResponse(persisted));
    }

    private void registerMailPublishAfterCommit(List<EmailOutbox> savedOutbox) {
        List<Long> ids = new ArrayList<>(savedOutbox.size());
        for (EmailOutbox row : savedOutbox) ids.add(row.getId());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishMailEvents(ids);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishMailEvents(ids);
            }
        });
    }

    private void publishMailEvents(List<Long> ids) {
        for (Long id : ids) {
            kafkaTemplate.send(mailDispatchTopic, String.valueOf(id), new MailDispatchEvent(id));
        }
    }
}
