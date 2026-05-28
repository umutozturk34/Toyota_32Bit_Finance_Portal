package com.finance.portfolio.dto.response;

import java.math.BigDecimal;

/** One asset/type slice within a performance point: its label, market value and PnL in TRY. */
public record PerformanceAssetDetail(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal pnlTry
) {}
