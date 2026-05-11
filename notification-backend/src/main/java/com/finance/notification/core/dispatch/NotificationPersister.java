package com.finance.notification.core.dispatch;

import com.finance.notification.config.NotificationAsyncConfig;
import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.mail.EmailOutboxRepository;
import com.finance.notification.core.mapper.NotificationMapper;
import com.finance.notification.core.model.Notification;
import com.finance.notification.core.repository.NotificationRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Log4j2
@Service
public class NotificationPersister {

    private final NotificationRepository notificationRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final NotificationStreamRegistry streamRegistry;
    private final NotificationMapper notificationMapper;
    private final Executor sseExecutor;

    public NotificationPersister(NotificationRepository notificationRepository,
                                 EmailOutboxRepository emailOutboxRepository,
                                 NotificationStreamRegistry streamRegistry,
                                 NotificationMapper notificationMapper,
                                 @Qualifier(NotificationAsyncConfig.SSE_EXECUTOR) Executor sseExecutor) {
        this.notificationRepository = notificationRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.streamRegistry = streamRegistry;
        this.notificationMapper = notificationMapper;
        this.sseExecutor = sseExecutor;
    }

    @Transactional
    public void persistBatch(List<Prepared> batch) {
        if (batch.isEmpty()) return;

        List<Notification> notifs = new ArrayList<>(batch.size());
        List<EmailOutbox> outboxRows = new ArrayList<>(batch.size());
        for (Prepared p : batch) {
            if (p.inappEntity() != null) notifs.add(p.inappEntity());
            if (p.outboxRow() != null) outboxRows.add(p.outboxRow());
        }

        List<Notification> persisted;
        try {
            persisted = notifs.isEmpty()
                    ? List.of()
                    : notificationRepository.saveAll(notifs);
            if (!outboxRows.isEmpty()) emailOutboxRepository.saveAll(outboxRows);
        } catch (DataAccessException ex) {
            log.error("Batch persist failed size={} inApp={} outbox={}: {}",
                    batch.size(), notifs.size(), outboxRows.size(), ex.getMessage());
            throw ex;
        }

        if (!persisted.isEmpty()) registerSsePushAfterCommit(persisted);
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
}
