package com.finance.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserStatusChangedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        String userSub,
        boolean enabled
) implements DomainEvent {

    public static UserStatusChangedEvent of(String userSub, boolean enabled) {
        return new UserStatusChangedEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                userSub,
                enabled);
    }

    @Override
    public String partitionKey() {
        return userSub;
    }
}
