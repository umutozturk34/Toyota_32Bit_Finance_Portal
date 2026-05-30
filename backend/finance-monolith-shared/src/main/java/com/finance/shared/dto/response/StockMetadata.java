package com.finance.shared.dto.response;

import com.finance.common.model.StockSegment;

import java.math.BigDecimal;

/** Stock-specific {@link MarketAssetMetadata}: market segment, exchange, volume, and intraday OHLC. */
public record StockMetadata(
        StockSegment stockSegment,
        Long volume,
        String exchange,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow
) implements MarketAssetMetadata {
}
