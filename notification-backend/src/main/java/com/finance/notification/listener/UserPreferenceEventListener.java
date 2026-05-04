package com.finance.notification.listener;

import com.finance.common.event.UserPreferencesUpdatedEvent;
import com.finance.notification.user.UserPreferenceCacheService;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class UserPreferenceEventListener {

    private final UserPreferenceCacheService cacheService;
    private final Cache<String, Boolean> processedEventIds;

    public UserPreferenceEventListener(UserPreferenceCacheService cacheService,
                                       @Qualifier("processedEventIds") Cache<String, Boolean> processedEventIds) {
        this.cacheService = cacheService;
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = "user.preferences.updated",
            groupId = "${spring.kafka.consumer.group-id}-user-prefs"
    )
    public void onUserPreferencesUpdated(UserPreferencesUpdatedEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate event {} for user {}, skip", event.eventId(), event.userSub());
            ack.acknowledge();
            return;
        }
        cacheService.upsertFromEvent(event);
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
        log.debug("Cached user preferences for {} (event {})", event.userSub(), event.eventId());
    }
}
