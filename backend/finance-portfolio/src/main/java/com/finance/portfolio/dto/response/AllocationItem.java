package com.finance.portfolio.dto.response;

import java.math.BigDecimal;

public record AllocationItem(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal percent,
        BigDecimal costTry,
        BigDecimal realizedPnlTry
) {}
