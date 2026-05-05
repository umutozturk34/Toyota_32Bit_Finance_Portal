package com.finance.notification.watchlist.dto;

import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WatchlistItemResponse(
        Long id,
        MarketType marketType,
        String assetCode,
        String assetName,
        String image,
        BigDecimal currentPrice,
        String note,
        BigDecimal deltaThreshold,
        BigDecimal lastSeenPrice,
        LocalDateTime lastSeenAt,
        LocalDateTime createdAt
) {
}
