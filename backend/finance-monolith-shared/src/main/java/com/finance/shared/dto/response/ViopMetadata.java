package com.finance.shared.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * VIOP (Turkish derivatives) {@link MarketAssetMetadata}: contract terms (underlying, expiry, size,
 * margin, settlement), option fields (side, strike, exercise style), and current bid/ask quotes.
 */
public record ViopMetadata(
        String kind,
        String category,
        String underlying,
        LocalDate expiryDate,
        BigDecimal contractSize,
        BigDecimal initialMargin,
        String settlementType,
        String currency,
        String optionSide,
        BigDecimal strikePrice,
        String exerciseStyle,
        BigDecimal volumeLot,
        BigDecimal bid,
        BigDecimal ask
) implements MarketAssetMetadata {
}
