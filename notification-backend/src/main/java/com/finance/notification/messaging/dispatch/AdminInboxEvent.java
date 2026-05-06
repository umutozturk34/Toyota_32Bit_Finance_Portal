package com.finance.notification.messaging.dispatch;

import com.finance.notification.messaging.dto.MessageResponse;

public record AdminInboxEvent(MessageResponse message) {
}
