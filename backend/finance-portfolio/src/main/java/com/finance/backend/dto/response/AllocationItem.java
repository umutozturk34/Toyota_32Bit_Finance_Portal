package com.finance.backend.dto.response;

import java.math.BigDecimal;

public record AllocationItem(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal percent
) {}
