package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.MessagePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Log4j2
@Component
@RequiredArgsConstructor
public class MessageNotificationListener {

    private final NotificationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageDispatch(MessageDispatchEvent event) {
        if (event.recipientSub() == null) {
            log.debug("Skipping notification dispatch for broadcast message sender={}", event.senderSub());
            return;
        }
        MessagePayload payload = new MessagePayload(event.senderSub(), event.body());
        dispatcher.dispatch(NotificationRequest.of(event.recipientSub(), payload));
    }
}
