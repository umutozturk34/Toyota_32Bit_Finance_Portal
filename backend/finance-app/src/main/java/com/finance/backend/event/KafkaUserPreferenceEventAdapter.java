package com.finance.backend.event;

import com.finance.backend.config.KafkaTopicsConfig;
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
        kafkaTemplate.send(KafkaTopicsConfig.USER_PREFERENCES_UPDATED_TOPIC, event.userSub(), event)
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
