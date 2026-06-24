package com.finance.notification.core.mail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.notification.config.NotificationOutboxProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Kafka consumer that sends one outbox email per {@link MailDispatchEvent}. It first atomically
 * claims the row (RELAYED to PROCESSING) so duplicate/redelivered events are ignored, then sends and
 * marks SENT, or on error increments attempts and reschedules with backoff until the max is hit
 * (then FAILED). Optimistic-lock conflicts mean another worker won and are skipped; metrics track
 * sent/retried/failed counts.
 */
@Log4j2
@Component
public class MailSendConsumer {

    private static final String GROUP_ID = "notification-mail-dispatch";

    private final EmailOutboxRepository repository;
    private final MailSender mailSender;
    private final ObjectMapper objectMapper;
    private final NotificationOutboxProperties properties;
    private final TransactionTemplate claimTx;
    private final Counter sentCounter;
    private final Counter retriedCounter;
    private final Counter failedCounter;

    /**
     * Wires collaborators and prepares dispatch state: a REQUIRES_NEW transaction template for the
     * atomic row-claim step (so the claim commits independently of the send) and the sent/retried/
     * failed Micrometer counters.
     */
    public MailSendConsumer(EmailOutboxRepository repository,
                            MailSender mailSender,
                            ObjectMapper objectMapper,
                            NotificationOutboxProperties properties,
                            PlatformTransactionManager txManager,
                            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.claimTx = new TransactionTemplate(txManager);
        this.claimTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.sentCounter = Counter.builder("mail.outbox.sent").register(meterRegistry);
        this.retriedCounter = Counter.builder("mail.outbox.retried").register(meterRegistry);
        this.failedCounter = Counter.builder("mail.outbox.failed").register(meterRegistry);
    }

    /**
     * Handles one mail-dispatch event: atomically claims the outbox row (RELAYED to PROCESSING) and,
     * if the claim fails because another worker or a prior delivery already took it, acknowledges and
     * exits — providing idempotency under redelivery. On a successful claim it reloads the row and
     * processes the send. The offset is always acknowledged so a stuck row is not retried via Kafka.
     */
    @KafkaListener(
            topics = "${app.kafka.topics.mail-dispatch}",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onDispatch(MailDispatchEvent event, Acknowledgment ack) {
        Boolean claimed = claimTx.execute(s -> repository.claimForProcessing(event.outboxId()) == 1);
        if (!Boolean.TRUE.equals(claimed)) {
            log.debug("Mail dispatch row id={} not RELAYED (already claimed or processed), skip",
                    event.outboxId());
            ack.acknowledge();
            return;
        }
        EmailOutbox row = repository.findById(event.outboxId())
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox row vanished after claim id=" + event.outboxId()));
        process(row);
        ack.acknowledge();
    }

    private void process(EmailOutbox row) {
        try {
            Map<String, Object> model = objectMapper.convertValue(row.getModel(),
                    new TypeReference<Map<String, Object>>() {});
            mailSender.sendBlocking(
                    row.getRecipientEmail(),
                    row.getSubject(),
                    row.getTemplateName(),
                    model,
                    row.getTheme(),
                    Locale.forLanguageTag(row.getLocale()));
            row.setStatus(EmailOutbox.Status.SENT);
            row.setSentAt(LocalDateTime.now());
            row.setRelayedAt(null);
            saveOrSkipOnConflict(row, "SENT");
            sentCounter.increment();
        } catch (OptimisticLockingFailureException stale) {
            log.warn("Email outbox row {} optimistic lock conflict, another worker won — skip", row.getId());
        } catch (RuntimeException ex) {
            handleFailure(row, ex);
        }
    }

    private void saveOrSkipOnConflict(EmailOutbox row, String transitionLabel) {
        try {
            repository.save(row);
        } catch (OptimisticLockingFailureException stale) {
            log.warn("Email outbox row {} {} update lost optimistic lock — another worker won, skip", row.getId(), transitionLabel);
        }
    }

    private void handleFailure(EmailOutbox row, RuntimeException ex) {
        LocalDateTime now = LocalDateTime.now();
        row.setAttempts(row.getAttempts() + 1);
        row.setLastAttemptAt(now);
        row.setLastError(truncate(ex.getMessage(), 1000));
        row.setRelayedAt(null);
        if (row.getAttempts() >= properties.maxAttempts()) {
            row.setStatus(EmailOutbox.Status.FAILED);
            failedCounter.increment();
            log.error("Email outbox row {} moved to FAILED after {} attempts to={}",
                    row.getId(), row.getAttempts(), row.getRecipientEmail());
        } else {
            row.setStatus(EmailOutbox.Status.PENDING);
            // Clamp: max-attempts may exceed the backoff schedule length; reuse the last backoff
            // for any further attempts instead of throwing IndexOutOfBounds.
            int i = Math.min(row.getAttempts() - 1, properties.backoffs().size() - 1);
            Duration backoff = properties.backoffs().get(i);
            row.setNextAttemptAt(now.plus(backoff));
            retriedCounter.increment();
            log.warn("Email outbox row {} attempt={} failed retryIn={} to={}: {}",
                    row.getId(), row.getAttempts(), backoff, row.getRecipientEmail(), ex.getMessage());
        }
        saveOrSkipOnConflict(row, "FAILURE");
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
