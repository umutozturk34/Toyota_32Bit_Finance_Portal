package com.finance.common.event;

import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record MarketUpdatedEvent(
        String eventId,
        MarketType marketType,
        OffsetDateTime occurredAt,
        String source,
        Map<String, BigDecimal> latestPrices
) {
    public static MarketUpdatedEvent of(MarketType marketType, String source) {
        return of(marketType, source, Map.of());
    }

    public static MarketUpdatedEvent of(MarketType marketType, String source, Map<String, BigDecimal> latestPrices) {
        return new MarketUpdatedEvent(
                UUID.randomUUID().toString(),
                marketType,
                OffsetDateTime.now(),
                source,
                latestPrices == null ? Map.of() : Map.copyOf(latestPrices)
        );
    }
}
