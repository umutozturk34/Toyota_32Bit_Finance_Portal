package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DerivativeMeta(
        String direction,
        String contractKind,
        BigDecimal contractSize,
        BigDecimal lockedMarginTry,
        LocalDate expiryDate,
        String currency,
        boolean closed,
        BigDecimal strikePrice,
        BigDecimal maxLossTry,
        BigDecimal maxGainTry,
        String displayName
) { }
