package com.finance.portfolio.dto.response;

import com.finance.portfolio.model.PerformanceEventType;

import java.math.BigDecimal;

/** A trade marker on a chart point: kind (added/sold), the asset, quantity and TRY value involved. */
public record PerformanceEvent(
        PerformanceEventType type,
        String assetType,
        String assetCode,
        BigDecimal quantity,
        BigDecimal valueTry
) {}
