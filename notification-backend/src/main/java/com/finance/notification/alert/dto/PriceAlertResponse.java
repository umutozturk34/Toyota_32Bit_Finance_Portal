package com.finance.notification.alert.dto;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceAlertResponse(
        Long id,
        MarketType marketType,
        String assetCode,
        String assetName,
        String image,
        BigDecimal currentPrice,
        AlertDirection direction,
        BigDecimal threshold,
        String currency,
        BigDecimal referencePrice,
        boolean active,
        LocalDateTime triggeredAt,
        LocalDateTime createdAt
) {
}
