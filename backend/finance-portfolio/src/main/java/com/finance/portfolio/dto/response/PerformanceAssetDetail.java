package com.finance.portfolio.dto.response;

import java.math.BigDecimal;

public record PerformanceAssetDetail(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal pnlTry
) {}
