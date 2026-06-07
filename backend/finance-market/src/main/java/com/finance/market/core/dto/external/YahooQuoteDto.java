package com.finance.market.core.dto.external;
import java.math.BigDecimal;
/**
 * A live quote parsed from Yahoo Finance: the current {@code regularMarketPrice} with the
 * {@code previousClose}, intraday open/high/low, traded {@code volume}, and the absolute/percentage
 * change for the session. Maps an external Yahoo payload into a provider-neutral quote shape.
 */
public record YahooQuoteDto(
        BigDecimal regularMarketPrice,
        BigDecimal previousClose,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        Long volume,
        BigDecimal regularMarketChange,
        BigDecimal regularMarketChangePercent
) {}
