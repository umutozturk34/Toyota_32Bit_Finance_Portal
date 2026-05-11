package com.finance.common.event;

import java.time.OffsetDateTime;

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
}
