package com.finance.market.core.dto.response;

import com.finance.shared.dto.response.MarketAssetMetadata;
import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Unified API view of any market asset: identity, price/change, and a market-specific metadata block. */
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
