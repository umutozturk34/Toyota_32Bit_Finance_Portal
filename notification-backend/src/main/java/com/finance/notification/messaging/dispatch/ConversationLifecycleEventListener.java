package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Log4j2
@Component
@RequiredArgsConstructor
public class ConversationLifecycleEventListener {

    private final NotificationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLifecycleChanged(ConversationLifecycleEvent event) {
        SystemPayload payload = switch (event.action()) {
            case CLOSED -> new SystemPayload(
                    "Sohbet kapatıldı",
                    "Yönetim sohbetinizi kapattı. Yeniden açılana kadar yeni mesaj gönderemezsiniz.",
                    event.adminSub());
            case REOPENED -> new SystemPayload(
                    "Sohbet yeniden açıldı",
                    "Yönetim sohbetinizi yeniden açtı. Mesajlaşmaya devam edebilirsiniz.",
                    event.adminSub());
            case DELETED -> new SystemPayload(
                    "Sohbet silindi",
                    "Yönetim sohbetinizi tamamen sildi. Geçmiş mesajlar artık erişilebilir değil.",
                    event.adminSub());
        };
        log.debug("Dispatching lifecycle notification user={} action={}", event.userSub(), event.action());
        dispatcher.dispatch(NotificationRequest.of(event.userSub(), payload));
    }
}
