package com.finance.common.event;

import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record MarketUpdatedEvent(
        String eventId,
        MarketType marketType,
        OffsetDateTime occurredAt,
        String source,
        Map<String, BigDecimal> latestPrices,
        Map<String, AssetSnapshot> latestSnapshots
) {
    public static MarketUpdatedEvent of(MarketType marketType, String source) {
        return of(marketType, source, Map.of());
    }

    public static MarketUpdatedEvent of(MarketType marketType, String source, Map<String, AssetSnapshot> latestSnapshots) {
        Map<String, AssetSnapshot> snapshots = latestSnapshots == null ? Map.of() : Map.copyOf(latestSnapshots);
        Map<String, BigDecimal> prices = snapshots.values().stream()
                .filter(s -> s.priceTry() != null)
                .collect(Collectors.toUnmodifiableMap(AssetSnapshot::code, AssetSnapshot::priceTry, (a, b) -> a));
        return new MarketUpdatedEvent(
                UUID.randomUUID().toString(),
                marketType,
                OffsetDateTime.now(),
                source,
                prices,
                snapshots
        );
    }
}
