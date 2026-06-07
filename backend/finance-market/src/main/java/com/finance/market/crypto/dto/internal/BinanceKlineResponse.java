package com.finance.market.crypto.dto.internal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * One Binance kline (candlestick) as returned by the klines REST endpoint. Binance encodes
 * each candle as a positional JSON array rather than an object, so {@link JsonFormat} with
 * {@code Shape.ARRAY} maps array positions to record components in declaration order; only the
 * leading OHLCV positions are bound and the remaining array elements are discarded via
 * {@link JsonIgnoreProperties}. {@code openTime} is the candle's open timestamp in epoch
 * milliseconds.
 *
 * @param openTime candle open time as Unix epoch milliseconds
 * @param open     opening price for the interval
 * @param high     highest price during the interval
 * @param low      lowest price during the interval
 * @param close    closing price for the interval
 * @param volume   base-asset volume traded during the interval
 */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceKlineResponse(
        Long openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {}
