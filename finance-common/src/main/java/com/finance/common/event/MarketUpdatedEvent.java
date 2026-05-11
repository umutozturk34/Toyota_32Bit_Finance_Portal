package com.finance.common.event;

import com.finance.common.model.MarketType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MarketUpdatedEvent(
        String eventId,
        MarketType marketType,
        OffsetDateTime occurredAt,
        String source
) implements DomainEvent {

    public static MarketUpdatedEvent of(MarketType marketType, String source) {
        return new MarketUpdatedEvent(
                UUID.randomUUID().toString(),
                marketType,
                OffsetDateTime.now(),
                source
        );
    }

    @Override
    public String partitionKey() {
        return marketType.name();
    }
}
