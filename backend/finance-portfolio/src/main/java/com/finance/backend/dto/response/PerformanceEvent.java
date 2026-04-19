package com.finance.backend.dto.response;

import com.finance.backend.model.PerformanceEventType;

import java.math.BigDecimal;

public record PerformanceEvent(
        PerformanceEventType type,
        String assetType,
        String assetCode,
        BigDecimal valueTry
) {}
