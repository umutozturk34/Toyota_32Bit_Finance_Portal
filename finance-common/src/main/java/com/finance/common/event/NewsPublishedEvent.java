package com.finance.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NewsPublishedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        String source
) implements DomainEvent {

    public static NewsPublishedEvent of(String source) {
        return new NewsPublishedEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                source
        );
    }

    @Override
    public String partitionKey() {
        return eventId;
    }
}
