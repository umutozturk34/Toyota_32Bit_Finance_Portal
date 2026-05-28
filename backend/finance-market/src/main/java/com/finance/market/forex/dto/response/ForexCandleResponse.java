package com.finance.market.forex.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** API forex candle: OHLC fields mirror the selling price, with buying/effective rates exposed alongside. */
public record ForexCandleResponse(
        LocalDateTime candleDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal sellingPrice,
        BigDecimal buyingPrice,
        BigDecimal effectiveBuyingPrice,
        BigDecimal effectiveSellingPrice
) {
}
