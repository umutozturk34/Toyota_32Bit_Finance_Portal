package com.finance.common.event;

import java.time.OffsetDateTime;

/**
 * Emitted when a user requests an email-change verification code; consumed by the notification
 * service to deliver the {@code code} to {@code newEmail}. Partitioned by user subject to preserve
 * per-user ordering.
 */
public record EmailChangeCodeRequestedEvent(
        String eventId,
        String userSub,
        String oldEmail,
        String newEmail,
        String code,
        String theme,
        OffsetDateTime expiresAt,
        OffsetDateTime occurredAt
) implements DomainEvent {

    @Override
    public String partitionKey() {
        return userSub;
    }

    @Override
    public EventTopic topic() {
        return EventTopic.USER_EMAIL_CHANGE_CODE;
    }
}
