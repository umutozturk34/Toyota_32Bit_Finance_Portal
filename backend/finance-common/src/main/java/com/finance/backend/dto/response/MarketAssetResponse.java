package com.finance.backend.dto.response;

import com.finance.backend.model.MarketType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketAssetResponse(
        String code,
        String name,
        String image,
        MarketType type,
        BigDecimal price,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        LocalDateTime lastUpdated,
        MarketAssetMetadata metadata
) {}
