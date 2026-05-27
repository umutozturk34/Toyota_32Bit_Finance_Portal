package com.finance.common.event;

import java.time.OffsetDateTime;

public record UserRegisteredEvent(
        String eventId,
        String userSub,
        OffsetDateTime occurredAt
) implements DomainEvent {

    @Override
    public String partitionKey() {
        return userSub;
    }

    @Override
    public EventTopic topic() {
        return EventTopic.USER_REGISTERED;
    }
}
