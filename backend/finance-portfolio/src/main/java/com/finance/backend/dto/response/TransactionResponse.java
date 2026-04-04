package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String assetType,
        String assetCode,
        String side,
        BigDecimal quantity,
        BigDecimal unitPriceTry,
        BigDecimal totalCostTry,
        BigDecimal feeTry,
        LocalDateTime createdAt
) {}
