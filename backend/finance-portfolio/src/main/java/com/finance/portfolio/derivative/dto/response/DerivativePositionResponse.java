package com.finance.portfolio.derivative.dto.response;

import com.finance.portfolio.derivative.model.DerivativeCloseReason;
import com.finance.portfolio.derivative.model.DerivativeDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Detailed derivative position view: flattened contract attributes plus entry/close, live PnL, notional exposure and locked margin. */
public record DerivativePositionResponse(
        Long id,
        String contractSymbol,
        String contractName,
        String contractKind,
        String contractCategory,
        String underlying,
        LocalDate expiryDate,
        BigDecimal contractSize,
        BigDecimal initialMargin,
        String currency,
        DerivativeDirection direction,
        LocalDate entryDate,
        BigDecimal entryPrice,
        BigDecimal quantityLot,
        LocalDate closeDate,
        BigDecimal closePrice,
        DerivativeCloseReason closeReason,
        BigDecimal currentPrice,
        BigDecimal pnl,
        BigDecimal nominalExposure,
        BigDecimal lockedMargin,
        boolean open,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
