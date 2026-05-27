package com.finance.notification.user;

import com.finance.common.event.UserRegisteredEvent;
import com.finance.notification.core.service.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

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
    public void onUserRegistered(UserRegisteredEvent event, Acknowledgment ack) {
        boolean created = preferenceService.ensureDefaultsExist(event.userSub());
        log.info("user.registered userSub={} preferencesCreated={}", event.userSub(), created);
        ack.acknowledge();
    }
}
