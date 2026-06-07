package com.finance.market.core.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single OHLCV candle returned to API clients for charting. {@code candleDate} is the bar's
 * period start; {@code open}/{@code high}/{@code low}/{@code close} are the price extremes and
 * boundaries for that period, and {@code volume} the traded quantity (may be {@code null} for
 * instruments without reported volume).
 */
public record CandleResponse(
        LocalDateTime candleDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {}
