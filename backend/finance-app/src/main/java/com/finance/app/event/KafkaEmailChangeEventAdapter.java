package com.finance.app.event;

import com.finance.common.event.EmailChangeCodeRequestedEvent;
import com.finance.common.event.EmailChangeEventPort;
import com.finance.common.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaEmailChangeEventAdapter implements EmailChangeEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishEmailChangeCode(EmailChangeCodeRequestedEvent event) {
        kafkaTemplate.send(KafkaTopics.USER_EMAIL_CHANGE_CODE, event.userSub(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish email-change.code-requested for {}: {}",
                                event.userSub(), ex.getMessage());
                    } else {
                        log.debug("Published email-change.code-requested for {}", event.userSub());
                    }
                });
    }
}
