package com.finance.shared.dto.response;

import com.finance.common.model.StockSegment;

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
