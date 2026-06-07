package com.finance.notification.user;

import com.finance.common.event.UserRegisteredEvent;
import com.finance.notification.core.service.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that seeds default notification preferences for each newly registered user, so they
 * have a baseline before any notification is dispatched.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class UserRegisteredListener {

    private static final String GROUP_ID = "notification-user-registered";

    private final NotificationPreferenceService preferenceService;

    @KafkaListener(
            topics = "${app.kafka.topics.user-registered}",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    /**
     * Handles one user-registered event by ensuring the user's default notification preferences
     * exist (idempotent: a no-op if already seeded), then acknowledges the offset.
     *
     * @param event the registration event carrying the new user's subject identifier
     * @param ack   the manual-ack handle committed after preferences are ensured
     */
    public void onUserRegistered(UserRegisteredEvent event, Acknowledgment ack) {
        boolean created = preferenceService.ensureDefaultsExist(event.userSub());
        log.info("user.registered userSub={} preferencesCreated={}", event.userSub(), created);
        ack.acknowledge();
    }
}
