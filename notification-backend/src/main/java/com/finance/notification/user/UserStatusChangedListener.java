package com.finance.notification.user;

import com.finance.common.event.UserStatusChangedEvent;
import com.finance.common.security.UserStatusPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class UserStatusChangedListener {

    private static final String GROUP_ID = "notification-user-status";

    private final UserStatusPort userStatus;

    @KafkaListener(
            topics = "${app.kafka.topics.user-status-changed}",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onStatusChanged(UserStatusChangedEvent event, Acknowledgment ack) {
        userStatus.invalidate(event.userSub());
        log.info("UserStatusChanged consumed user={} enabled={} cache invalidated",
                event.userSub(), event.enabled());
        ack.acknowledge();
    }
}
