package com.finance.common.dto.response;

import com.finance.market.core.model.StockSegment;

import java.math.BigDecimal;

public record StockMetadata(
        StockSegment stockSegment,
        Long volume,
        String exchange,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow
) implements MarketAssetMetadata {
}
