package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Derivative-specific fields nested in a position response: direction, contract kind/size, margin, expiry, strike,
 * option side (CALL/PUT) and option max loss/gain. {@code optionSide} lets the UI flag a SHORT option's downside —
 * a short CALL is unbounded, a short PUT is bounded by the strike — which the premium-only max gain otherwise hides.
 */
public record DerivativeMeta(
        String direction,
        String contractKind,
        BigDecimal contractSize,
        BigDecimal lockedMarginTry,
        LocalDate expiryDate,
        String currency,
        boolean closed,
        BigDecimal strikePrice,
        String optionSide,
        BigDecimal maxLossTry,
        BigDecimal maxGainTry,
        String displayName
) { }
