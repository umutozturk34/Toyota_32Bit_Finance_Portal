package com.finance.notification.alert.dto;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Price alert as returned to the client, enriched with live snapshot data (current price, change,
 * image) when available; snapshot-derived fields are null when no snapshot exists.
 */
public record PriceAlertResponse(
        Long id,
        MarketType marketType,
        String assetCode,
        String assetName,
        String image,
        BigDecimal currentPrice,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        AlertDirection direction,
        BigDecimal threshold,
        String currency,
        BigDecimal referencePrice,
        boolean active,
        LocalDateTime triggeredAt,
        LocalDateTime createdAt
) {
}
