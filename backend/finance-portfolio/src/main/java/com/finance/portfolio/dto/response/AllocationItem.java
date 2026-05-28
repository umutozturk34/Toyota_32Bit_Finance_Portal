package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/** One slice of an allocation breakdown: bucket label/type, TRY value and share, with optional cost and realized PnL (per currency for the realized-PnL view). */
public record AllocationItem(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal percent,
        BigDecimal costTry,
        BigDecimal realizedPnlTry,
        Map<String, BigDecimal> realizedPnlByCurrency
) {
    public AllocationItem(String label, String assetType, BigDecimal valueTry, BigDecimal percent,
                          BigDecimal costTry, BigDecimal realizedPnlTry) {
        this(label, assetType, valueTry, percent, costTry, realizedPnlTry, Map.of());
    }
}
