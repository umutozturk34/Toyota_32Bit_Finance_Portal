package com.finance.notification.messaging.dispatch;

public record ConversationLifecycleEvent(
        String userSub,
        String adminSub,
        Action action
) {
    public enum Action {
        CLOSED,
        REOPENED,
        DELETED
    }
}
