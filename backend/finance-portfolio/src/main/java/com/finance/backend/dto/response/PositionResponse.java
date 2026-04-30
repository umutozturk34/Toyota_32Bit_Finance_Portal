package com.finance.backend.dto.response;

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
        BigDecimal currentPriceTry,
        BigDecimal entryValueTry,
        BigDecimal marketValueTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent
) {}
