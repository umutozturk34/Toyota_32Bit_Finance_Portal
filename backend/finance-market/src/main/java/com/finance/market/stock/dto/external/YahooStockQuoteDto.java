package com.finance.market.stock.dto.external;
import java.math.BigDecimal;
/**
 * Normalized snapshot of a single stock quote parsed from the external Yahoo Finance API, used to
 * carry real-time pricing into the market module before it is mapped onto domain entities.
 *
 * @param symbol         ticker symbol of the instrument
 * @param name           display name of the instrument
 * @param currentPrice   latest traded price
 * @param previousClose  prior session's closing price (basis for the change figures)
 * @param changeAmount   absolute price change versus {@code previousClose}
 * @param changePercent  percentage price change versus {@code previousClose}
 * @param openPrice      current session's opening price
 * @param dayHigh        highest price of the current session
 * @param dayLow         lowest price of the current session
 * @param volume         number of shares traded in the current session
 * @param exchange       exchange/market the instrument trades on
 * @param currency       currency code the prices are quoted in
 */
public record YahooStockQuoteDto(
        String symbol,
        String name,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        long volume,
        String exchange,
        String currency
) {}
