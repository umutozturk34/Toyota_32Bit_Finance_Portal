package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationStreamRegistry;
import com.finance.notification.messaging.presence.ActiveConversationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Log4j2
@Component
@RequiredArgsConstructor
public class AdminInboxEventListener {

    private static final String EVENT_NAME = "admin-inbox";
    private static final String SILENT_EVENT_NAME = "admin-inbox-silent";
    private static final String ADMIN_THREAD_KEY_PREFIX = "user:";

    private final NotificationStreamRegistry streamRegistry;
    private final ActiveConversationRegistry presence;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserMessageReachedAdminInbox(AdminInboxEvent event) {
        boolean adminViewing = presence.isAnyoneActiveOn(ADMIN_THREAD_KEY_PREFIX + event.message().senderSub());
        String eventName = adminViewing ? SILENT_EVENT_NAME : EVENT_NAME;
        log.debug("Pushing {} SSE event message={} adminViewing={}",
                eventName, event.message().id(), adminViewing);
        streamRegistry.publishToAdmins(eventName, event.message());
    }
}
