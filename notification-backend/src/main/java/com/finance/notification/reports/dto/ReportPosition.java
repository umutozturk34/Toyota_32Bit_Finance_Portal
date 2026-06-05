package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single portfolio position (lot) as shown in the report, with entry/exit, valuation and P/L.
 *
 * <p>{@code direction} (LONG/SHORT, null for non-derivatives) is required so VİOP P&L keeps its
 * sign once re-derived in another currency: {@code marketValueTry} is direction-blind notional,
 * so a SHORT's USD/EUR P&L from {@code marketValueConv − entryValueConv} would otherwise be
 * inverted. The backend sends it nested under {@code derivative.direction}, which the
 * deserialization creator below lifts up.
 */
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
        BigDecimal pnlPercent,
        String direction
) {
    /** Back-compat constructor for callers/tests that predate the {@code direction} field. */
    public ReportPosition(
            Long id, String assetType, String assetCode, String assetName, BigDecimal quantity,
            LocalDateTime entryDate, BigDecimal entryPrice, LocalDateTime exitDate, BigDecimal exitPrice,
            BigDecimal currentPriceTry, BigDecimal entryValueTry, BigDecimal marketValueTry,
            BigDecimal pnlTry, BigDecimal pnlPercent) {
        this(id, assetType, assetCode, assetName, quantity, entryDate, entryPrice, exitDate, exitPrice,
                currentPriceTry, entryValueTry, marketValueTry, pnlTry, pnlPercent, null);
    }

    /**
     * Jackson entry point for the {@code /positions} wire: lifts {@code direction} out of the nested
     * {@code derivative} object so the flat record carries it. Non-derivative rows have no
     * {@code derivative} node, leaving {@code direction} null.
     */
    @JsonCreator
    static ReportPosition fromWire(
            @JsonProperty("id") Long id,
            @JsonProperty("assetType") String assetType,
            @JsonProperty("assetCode") String assetCode,
            @JsonProperty("assetName") String assetName,
            @JsonProperty("quantity") BigDecimal quantity,
            @JsonProperty("entryDate") LocalDateTime entryDate,
            @JsonProperty("entryPrice") BigDecimal entryPrice,
            @JsonProperty("exitDate") LocalDateTime exitDate,
            @JsonProperty("exitPrice") BigDecimal exitPrice,
            @JsonProperty("currentPriceTry") BigDecimal currentPriceTry,
            @JsonProperty("entryValueTry") BigDecimal entryValueTry,
            @JsonProperty("marketValueTry") BigDecimal marketValueTry,
            @JsonProperty("pnlTry") BigDecimal pnlTry,
            @JsonProperty("pnlPercent") BigDecimal pnlPercent,
            @JsonProperty("direction") String direction,
            @JsonProperty("derivative") Derivative derivative) {
        return new ReportPosition(id, assetType, assetCode, assetName, quantity, entryDate, entryPrice,
                exitDate, exitPrice, currentPriceTry, entryValueTry, marketValueTry, pnlTry, pnlPercent,
                direction != null ? direction : (derivative != null ? derivative.direction() : null));
    }

    /** Minimal projection of the backend's nested derivative metadata; only direction is consumed here. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Derivative(String direction) {}

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
