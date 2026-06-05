package com.finance.user.dto;

import java.time.Instant;

/**
 * A single recently-selected search result: the asset's {@code code}, its {@code type}
 * (e.g. STOCK/CRYPTO/BOND), a display {@code name}, and when it was selected. This is also the
 * element shape persisted in the {@code user_recent_searches.items} JSON array.
 */
public record RecentSearchItem(
        String code,
        String type,
        String name,
        Instant searchedAt
) {
}
