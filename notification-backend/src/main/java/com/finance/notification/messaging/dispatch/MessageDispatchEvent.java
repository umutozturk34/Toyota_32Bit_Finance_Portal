package com.finance.notification.messaging.dispatch;

public record MessageDispatchEvent(
        String recipientSub,
        String senderSub,
        String body
) {
}
