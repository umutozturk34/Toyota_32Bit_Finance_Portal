package com.finance.portfolio.dto.response;

import com.finance.portfolio.model.PerformanceEventType;

import java.math.BigDecimal;

public record PerformanceEvent(
        PerformanceEventType type,
        String assetType,
        String assetCode,
        BigDecimal valueTry
) {}
