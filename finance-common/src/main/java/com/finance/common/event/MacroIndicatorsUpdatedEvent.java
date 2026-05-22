package com.finance.common.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MacroIndicatorsUpdatedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        String source,
        List<String> changedCodes
) implements DomainEvent {

    public static MacroIndicatorsUpdatedEvent of(String source, List<String> changedCodes) {
        return new MacroIndicatorsUpdatedEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                source,
                changedCodes == null ? List.of() : List.copyOf(changedCodes)
        );
    }

    @Override
    public String partitionKey() {
        return eventId;
    }
}
