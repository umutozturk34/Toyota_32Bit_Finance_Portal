package com.finance.notification.messaging.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.common.security.UserStatusPort;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.NotificationStreamRegistry;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.user.UserPreferenceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Locale;
import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class ConversationLifecycleEventListener {

    private static final String SILENT_EVENT_NAME = "notification-silent";

    private final NotificationDispatcher dispatcher;
    private final NotificationStreamRegistry streamRegistry;
    private final UserStatusPort userStatus;
    private final UserPreferenceCacheService userPreferenceCacheService;
    private final Translator translator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLifecycleChanged(ConversationLifecycleEvent event) {
        if (!userStatus.isActive(event.userSub())) {
            log.debug("Lifecycle notification suppressed (user inactive) user={} action={}",
                    event.userSub(), event.action());
            return;
        }
        Locale locale = userPreferenceCacheService.resolveLocale(event.userSub());
        String keyPrefix = switch (event.action()) {
            case CLOSED -> "notif.conversationLifecycle.closed";
            case REOPENED -> "notif.conversationLifecycle.reopened";
            case DELETED -> "notif.conversationLifecycle.deleted";
        };
        SystemPayload payload = new SystemPayload(
                translator.translate(keyPrefix + ".title", locale),
                translator.translate(keyPrefix + ".body", locale),
                event.adminSub());
        log.debug("Dispatching lifecycle notification user={} action={}", event.userSub(), event.action());
        dispatcher.dispatch(NotificationRequest.of(event.userSub(), payload));
        streamRegistry.publishToUser(event.userSub(), SILENT_EVENT_NAME,
                Map.of("type", "CONVERSATION_LIFECYCLE", "action", event.action().name()));
    }
}
