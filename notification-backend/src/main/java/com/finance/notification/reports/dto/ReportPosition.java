package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A single portfolio position (lot) as shown in the report, with entry/exit, valuation and P/L. */
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
    /** Whether the lot is closed; VIOP positions are detected via the "KAPALI" name marker, others by exit date. */
    public boolean isClosed() {
        if ("VIOP".equalsIgnoreCase(assetType)) {
            return assetName != null && assetName.contains("KAPALI");
        }
        return exitDate != null;
    }

    /** Display name without the trailing closed marker; null when the name adds nothing over the code. */
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
