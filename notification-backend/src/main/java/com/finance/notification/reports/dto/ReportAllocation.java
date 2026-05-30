package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Per-asset-type allocation slice with value, share, cost and realized P/L (all in TRY). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportAllocation(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal percent,
        BigDecimal costTry,
        BigDecimal realizedPnlTry
) {}
