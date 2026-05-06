package com.finance.notification.watchlist.dto;

import java.time.LocalDateTime;

public record WatchlistResponse(
        Long id,
        String name,
        boolean isDefault,
        long itemCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
