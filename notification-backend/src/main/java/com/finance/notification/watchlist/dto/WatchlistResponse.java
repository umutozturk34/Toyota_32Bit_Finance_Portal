package com.finance.notification.watchlist.dto;

import java.time.LocalDateTime;

/** A watchlist as returned to the client, including its item count and default-list flag. */
public record WatchlistResponse(
        Long id,
        String name,
        boolean isDefault,
        long itemCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
