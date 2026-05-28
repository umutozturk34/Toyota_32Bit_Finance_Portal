package com.finance.notification.core.mail;

import com.finance.common.event.KafkaTopicsProperties;
import com.finance.notification.config.NotificationOutboxProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Background relay that polls the outbox for due PENDING rows, flips them to RELAYED and publishes a
 * Kafka dispatch event per row after commit (so events are only emitted for committed state). A
 * second schedule reclaims rows stuck in RELAYED/PROCESSING past the timeout back to PENDING, which
 * recovers emails whose consumer crashed before completing.
 */
@Log4j2
@Component
public class OutboxRelayWorker {

    private static final String RECLAIM_SQL = """
            UPDATE email_outbox
            SET status = 'PENDING', relayed_at = NULL
            WHERE status IN ('RELAYED', 'PROCESSING')
              AND relayed_at < ?
            """;

    private final EmailOutboxRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationOutboxProperties properties;
    private final KafkaTopicsProperties topics;

    public OutboxRelayWorker(EmailOutboxRepository repository,
                             KafkaTemplate<String, Object> kafkaTemplate,
                             JdbcTemplate jdbcTemplate,
                             NotificationOutboxProperties properties,
                             KafkaTopicsProperties topics) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.topics = topics;
    }

    @Scheduled(fixedDelayString = "${notification.email.outbox.poll-delay}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void relayPending() {
        List<EmailOutbox> batch = repository.findPendingForProcessing(PageRequest.of(0, properties.batchSize()));
        if (batch.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = new ArrayList<>(batch.size());
        for (EmailOutbox row : batch) {
            row.setStatus(EmailOutbox.Status.RELAYED);
            row.setRelayedAt(now);
            ids.add(row.getId());
        }
        repository.saveAll(batch);
        publishAfterCommit(ids);
        log.info("Email outbox relayed batch={} ids={}", batch.size(), ids);
    }

    @Scheduled(fixedDelayString = "${notification.email.outbox.reclaim-delay}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reclaimStaleRelayed() {
        Timestamp threshold = Timestamp.valueOf(LocalDateTime.now().minus(properties.reclaimAfter()));
        int reset = jdbcTemplate.update(RECLAIM_SQL, threshold);
        if (reset > 0) {
            log.warn("Email outbox reclaimed staleRelayedRows={}", reset);
        }
    }

    private void publishAfterCommit(List<Long> ids) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long id : ids) {
                    kafkaTemplate.send(topics.mailDispatch(), String.valueOf(id), new MailDispatchEvent(id));
                }
            }
        });
    }
}
