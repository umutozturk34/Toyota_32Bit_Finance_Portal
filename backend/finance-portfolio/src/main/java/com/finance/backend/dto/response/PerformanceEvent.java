package com.finance.backend.dto.response;

import java.math.BigDecimal;

public record PerformanceEvent(
        String type,
        String assetType,
        String assetCode,
        BigDecimal valueTry
) {}
