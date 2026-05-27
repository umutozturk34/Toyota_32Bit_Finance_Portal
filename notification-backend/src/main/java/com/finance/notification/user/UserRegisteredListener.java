package com.finance.notification.user;

import com.finance.common.event.UserRegisteredEvent;
import com.finance.notification.core.service.NotificationPreferenceService;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class UserRegisteredListener {

    private static final String GROUP_ID = "notification-user-registered";

    private final NotificationPreferenceService preferenceService;
    private final Cache<String, Boolean> processedEventIds;

    public UserRegisteredListener(NotificationPreferenceService preferenceService,
                                  @Qualifier("userRegisteredProcessedEventIds")
                                  Cache<String, Boolean> processedEventIds) {
        this.preferenceService = preferenceService;
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.user-registered}",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onUserRegistered(UserRegisteredEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate user.registered event {} userSub={}, skip",
                    event.eventId(), event.userSub());
            ack.acknowledge();
            return;
        }
        boolean created = preferenceService.ensureDefaultsExist(event.userSub());
        log.info("user.registered userSub={} preferencesCreated={}", event.userSub(), created);
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
