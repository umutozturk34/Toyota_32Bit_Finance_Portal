package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportPosition(
        Long id,
        String assetType,
        String assetCode,
        String assetName,
        BigDecimal quantity,
        LocalDateTime entryDate,
        BigDecimal entryPrice,
        LocalDateTime exitDate,
        BigDecimal exitPrice,
        BigDecimal currentPriceTry,
        BigDecimal entryValueTry,
        BigDecimal marketValueTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent
) {
    public boolean isClosed() {
        if ("VIOP".equalsIgnoreCase(assetType)) {
            return assetName != null && assetName.contains("KAPALI");
        }
        return exitDate != null;
    }

    public String displayName() {
        if (assetName == null || assetName.equals(assetCode)) return null;
        return assetName.replaceAll("\\s·\\sKAPALI$", "");
    }

    public String displayQuantity() {
        if (quantity == null) return "—";
        BigDecimal stripped = quantity.stripTrailingZeros();
        if (stripped.scale() < 0) stripped = stripped.setScale(0);
        return stripped.toPlainString();
    }
}
