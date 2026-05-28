package com.finance.common.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Signals that one or more macroeconomic indicator series changed, listing the affected
 * {@code changedCodes}. Build via {@link #of(String, java.util.List)}, which assigns a random
 * event id and defensively copies the codes (treating null as empty).
 */
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

    @Override
    public EventTopic topic() {
        return EventTopic.MACRO_INDICATORS_UPDATED;
    }
}
