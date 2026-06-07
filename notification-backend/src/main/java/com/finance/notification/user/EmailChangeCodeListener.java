package com.finance.notification.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.common.event.EmailChangeCodeRequestedEvent;
import com.finance.common.i18n.Translator;
import com.finance.common.security.UserStatusPort;
import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.mail.EmailOutboxRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Kafka consumer that, on an email-change-code request, enqueues a verification-code email to the
 * user's OLD address (where they can still receive mail). Deduplicates by event id, suppresses
 * inactive users, and uses the user's preferred locale; the row goes through the outbox for delivery.
 */
@Log4j2
@Component
public class EmailChangeCodeListener {

    private final EmailOutboxRepository emailOutboxRepository;
    private final ObjectMapper objectMapper;
    private final Cache<String, Boolean> processedEventIds;
    private final UserStatusPort userStatus;
    private final UserPreferenceCacheService userPreferenceCacheService;
    private final Translator translator;

    /**
     * Wires the outbox repository, JSON mapper, the shared processed-event-id cache (for idempotent
     * consumption), the user-status port (to suppress inactive users), the preference cache (to
     * resolve the recipient's locale), and the i18n translator (for the localized subject line).
     */
    public EmailChangeCodeListener(EmailOutboxRepository emailOutboxRepository,
                                   ObjectMapper objectMapper,
                                   @Qualifier("processedEventIds") Cache<String, Boolean> processedEventIds,
                                   UserStatusPort userStatus,
                                   UserPreferenceCacheService userPreferenceCacheService,
                                   Translator translator) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.objectMapper = objectMapper;
        this.processedEventIds = processedEventIds;
        this.userStatus = userStatus;
        this.userPreferenceCacheService = userPreferenceCacheService;
        this.translator = translator;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.user-email-change-code}",
            groupId = "${spring.kafka.consumer.group-id}-email-change"
    )
    /**
     * Handles one email-change-code event: skips duplicates (by event id) and inactive users,
     * otherwise builds the localized template model (code, old/new email, minutes until expiry,
     * floored to at least one) and persists a PENDING outbox row addressed to the OLD email. The
     * event id is recorded as processed and the offset is acknowledged in every terminal path, so
     * a handled message is never reprocessed.
     *
     * @param event the email-change-code request carrying the user, addresses, code and expiry
     * @param ack   the manual-ack handle committed once the message is fully handled or skipped
     */
    public void onEmailChangeCode(EmailChangeCodeRequestedEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate email change event {} for {}, skip", event.eventId(), event.userSub());
            ack.acknowledge();
            return;
        }
        if (!userStatus.isActive(event.userSub())) {
            log.info("Email change code suppressed (user inactive) user={}", event.userSub());
            processedEventIds.put(event.eventId(), Boolean.TRUE);
            ack.acknowledge();
            return;
        }
        long minutesLeft = Math.max(1L, Duration.between(OffsetDateTime.now(), event.expiresAt()).toMinutes());

        Map<String, Object> model = new HashMap<>();
        model.put("code", event.code());
        model.put("newEmail", event.newEmail());
        model.put("oldEmail", event.oldEmail());
        model.put("minutesLeft", minutesLeft);

        Locale locale = userPreferenceCacheService.resolveLocale(event.userSub());
        String subject = translator.translate("email.changeCode.subject", locale);

        EmailOutbox row = EmailOutbox.builder()
                .recipientEmail(event.oldEmail())
                .subject(subject)
                .templateName("email-change-code")
                .model(objectMapper.valueToTree(model))
                .theme(event.theme())
                .locale(locale.toLanguageTag())
                .status(EmailOutbox.Status.PENDING)
                .build();
        emailOutboxRepository.save(row);

        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
        log.info("Email change code enqueued user={} to={} outboxId={}",
                event.userSub(), event.oldEmail(), row.getId());
    }
}
