package com.finance.notification.watchlist.dto;

import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A watchlist item as returned to the client, enriched with live snapshot data (price, change,
 * image, currency) when available alongside the user's note, delta threshold and last-seen baseline.
 */
public record WatchlistItemResponse(
        Long id,
        MarketType marketType,
        String assetCode,
        String assetName,
        String image,
        BigDecimal currentPrice,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        String currency,
        String note,
        BigDecimal deltaThreshold,
        BigDecimal lastSeenPrice,
        LocalDateTime lastSeenAt,
        LocalDateTime createdAt
) {
}
