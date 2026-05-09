package com.finance.notification.user;

import com.finance.common.event.EmailChangeCodeRequestedEvent;
import com.finance.common.event.KafkaTopics;
import com.finance.common.security.UserStatusPort;
import com.finance.notification.core.mail.MailSender;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Log4j2
@Component
public class EmailChangeCodeListener {

    private final MailSender mailSender;
    private final Cache<String, Boolean> processedEventIds;
    private final UserStatusPort userStatus;

    public EmailChangeCodeListener(MailSender mailSender,
                                   @Qualifier("processedEventIds") Cache<String, Boolean> processedEventIds,
                                   UserStatusPort userStatus) {
        this.mailSender = mailSender;
        this.processedEventIds = processedEventIds;
        this.userStatus = userStatus;
    }

    @KafkaListener(
            topics = KafkaTopics.USER_EMAIL_CHANGE_CODE,
            groupId = "${spring.kafka.consumer.group-id}-email-change"
    )
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

        mailSender.send(event.oldEmail(), "Finance Portal · E-posta değişikliği onayı",
                "email-change-code", model, event.theme());

        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
        log.info("Email change code dispatched user={} to={}", event.userSub(), event.oldEmail());
    }
}
