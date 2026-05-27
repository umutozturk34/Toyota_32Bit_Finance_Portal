package com.finance.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PortfolioUpdatedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        String source
) implements DomainEvent {

    public static PortfolioUpdatedEvent of(String source) {
        return new PortfolioUpdatedEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                source
        );
    }

    @Override
    public String partitionKey() {
        return eventId;
    }

    @Override
    public EventTopic topic() {
        return EventTopic.PORTFOLIO_UPDATED;
    }
}
