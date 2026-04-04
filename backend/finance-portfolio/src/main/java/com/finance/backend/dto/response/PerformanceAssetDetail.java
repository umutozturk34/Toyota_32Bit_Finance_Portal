package com.finance.backend.dto.response;

import java.math.BigDecimal;

public record PerformanceAssetDetail(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal pnlTry
) {}
