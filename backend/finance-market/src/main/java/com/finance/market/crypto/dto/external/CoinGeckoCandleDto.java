package com.finance.market.crypto.dto.external;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * Internal representation of a single crypto OHLCV candle sourced from the CoinGecko market-chart
 * endpoint. Tagged with the CoinGecko {@code coinId} so candles can be attributed to a coin during
 * ingestion. {@code candleDate} is the bar's period start, the OHLC fields are its price
 * boundaries, and {@code volume} the traded quantity. Note: CoinGecko is used for the market screen;
 * portfolio valuation deliberately prices crypto from Binance candles instead.
 */
public record CoinGeckoCandleDto(
        String coinId,
        LocalDateTime candleDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {}
