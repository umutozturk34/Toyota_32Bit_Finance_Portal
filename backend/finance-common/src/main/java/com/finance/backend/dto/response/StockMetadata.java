package com.finance.backend.dto.response;

import com.finance.backend.model.StockSegment;

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
