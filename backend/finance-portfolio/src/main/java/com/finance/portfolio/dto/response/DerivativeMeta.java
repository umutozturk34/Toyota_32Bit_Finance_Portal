package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Derivative-specific fields nested in a position response: direction, contract kind/size, margin, expiry, strike and option max loss/gain. */
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
