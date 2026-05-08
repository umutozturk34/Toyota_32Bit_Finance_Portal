package com.finance.app.event;
import com.finance.common.event.KafkaTopics;

import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;
import com.finance.common.event.KafkaTopics;
import com.finance.common.event.UserPreferenceEventPort;
import com.finance.common.event.UserPreferencesUpdatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaUserPreferenceEventAdapter implements UserPreferenceEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishUserPreferencesUpdated(UserPreferencesUpdatedEvent event) {
        kafkaTemplate.send(KafkaTopics.USER_PREFERENCES_UPDATED, event.userSub(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish user.preferences.updated for {}: {}",
                                event.userSub(), ex.getMessage());
                    } else {
                        log.debug("Published user.preferences.updated for {}", event.userSub());
                    }
                });
    }
}
