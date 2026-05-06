package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationStreamRegistry;
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

    private final NotificationStreamRegistry streamRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserMessageReachedAdminInbox(AdminInboxEvent event) {
        log.debug("Pushing admin-inbox SSE event message={}", event.message().id());
        streamRegistry.publishToAdmins(EVENT_NAME, event.message());
    }
}
