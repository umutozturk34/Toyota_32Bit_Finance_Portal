package com.finance.market.viop.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Live VIOP quote with day/week/month/year stats and contract limits; fields may be null when the
 * upstream payload omits them, so writers apply only present values.
 */
public record ViopQuoteSnapshot(
        String symbol,
        Instant updatedAt,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal last,
        BigDecimal dayClose,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal volumeLot,
        BigDecimal volumeTry,
        BigDecimal settlement,
        BigDecimal preSettlement,
        BigDecimal limitUp,
        BigDecimal limitDown,
        BigDecimal weekHigh,
        BigDecimal weekLow,
        BigDecimal weekClose,
        BigDecimal monthHigh,
        BigDecimal monthLow,
        BigDecimal monthClose,
        BigDecimal yearClose,
        BigDecimal prevYearClose,
        BigDecimal initialMargin,
        BigDecimal priceStep
) { }
