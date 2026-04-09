package com.finance.backend.dto.request;

import java.math.BigDecimal;

public record TransactionRequest(
        String assetType,
        String assetCode,
        String side,
        BigDecimal quantity,
        BigDecimal amountTry,
        BigDecimal feeTry
) {}
