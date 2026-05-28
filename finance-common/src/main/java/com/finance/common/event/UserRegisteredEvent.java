package com.finance.common.event;

import java.time.OffsetDateTime;

/**
 * Signals that a new user (identified by {@code userSub}) registered; partitioned by subject so
 * downstream consumers process a user's events in order.
 */
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
