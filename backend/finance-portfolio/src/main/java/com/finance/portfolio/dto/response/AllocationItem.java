package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One slice of an allocation breakdown: bucket label/type, TRY value and share, with optional cost and
 * realized PnL. For the realized-PnL view both the realized gain and the cost basis carry per-currency
 * (USD/EUR) frames converted at the position's own close/entry-date FX, so the front end can render gain
 * and cost on the same FX date instead of mixing per-date realized with today's-spot cost.
 */
public record AllocationItem(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal percent,
        BigDecimal costTry,
        BigDecimal realizedPnlTry,
        Map<String, BigDecimal> realizedPnlByCurrency,
        Map<String, BigDecimal> costByCurrency
) {
    public AllocationItem(String label, String assetType, BigDecimal valueTry, BigDecimal percent,
                          BigDecimal costTry, BigDecimal realizedPnlTry) {
        this(label, assetType, valueTry, percent, costTry, realizedPnlTry, Map.of(), Map.of());
    }
}
