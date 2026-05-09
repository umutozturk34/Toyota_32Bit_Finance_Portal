package com.finance.notification.messaging.dispatch;

import com.finance.common.security.UserStatusPort;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.NotificationStreamRegistry;
import com.finance.notification.core.dispatch.payload.MessagePayload;
import com.finance.notification.messaging.presence.ActiveConversationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class MessageNotificationListener {

    private static final String SILENT_EVENT_NAME = "notification-silent";
    private static final String USER_THREAD_KEY = "admin";

    private final NotificationDispatcher dispatcher;
    private final NotificationStreamRegistry streamRegistry;
    private final ActiveConversationRegistry presence;
    private final UserStatusPort userStatus;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageDispatch(MessageDispatchEvent event) {
        if (event.recipientSub() == null) {
            log.debug("Skipping notification dispatch for broadcast message sender={}", event.senderSub());
            return;
        }
        if (!userStatus.isActive(event.recipientSub())) {
            log.debug("Message notification suppressed (recipient inactive) recipient={}", event.recipientSub());
            return;
        }
        boolean userViewing = presence.getActiveKey(event.recipientSub())
                .map(USER_THREAD_KEY::equals)
                .orElse(false);
        if (userViewing) {
            log.debug("User actively viewing admin thread, sending silent SSE only recipient={}",
                    event.recipientSub());
            streamRegistry.publishToUser(event.recipientSub(), SILENT_EVENT_NAME,
                    Map.of("senderSub", event.senderSub(), "type", "MESSAGE"));
            return;
        }
        MessagePayload payload = new MessagePayload(event.senderSub(), event.body());
        dispatcher.dispatch(NotificationRequest.of(event.recipientSub(), payload));
    }
}
