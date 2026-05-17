package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionResponse(
        Long id,
        String assetType,
        String assetCode,
        String assetName,
        String assetImage,
        BigDecimal quantity,
        LocalDateTime entryDate,
        BigDecimal entryPrice,
        LocalDateTime exitDate,
        BigDecimal exitPrice,
        BigDecimal realizedPnlTry,
        BigDecimal currentPriceTry,
        BigDecimal entryValueTry,
        BigDecimal marketValueTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent,
        DerivativeMeta derivative
) {
    public PositionResponse(Long id, String assetType, String assetCode, String assetName,
                            String assetImage, BigDecimal quantity, LocalDateTime entryDate,
                            BigDecimal entryPrice, BigDecimal currentPriceTry,
                            BigDecimal entryValueTry, BigDecimal marketValueTry,
                            BigDecimal pnlTry, BigDecimal pnlPercent) {
        this(id, assetType, assetCode, assetName, assetImage, quantity, entryDate, entryPrice,
                null, null, null,
                currentPriceTry, entryValueTry, marketValueTry, pnlTry, pnlPercent, null);
    }
}
