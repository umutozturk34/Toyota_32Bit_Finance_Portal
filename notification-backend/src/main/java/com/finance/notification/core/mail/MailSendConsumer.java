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
            Duration backoff = properties.backoffs().get(row.getAttempts() - 1);
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
