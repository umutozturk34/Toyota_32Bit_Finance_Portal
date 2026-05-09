package com.finance.shared.dto.response;

import java.math.BigDecimal;

public record CommodityMetadata(
        BigDecimal currentPriceUsd,
        BigDecimal previousPriceUsd,
        String unit,
        BigDecimal openPrice,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        Long volume
) implements MarketAssetMetadata {
}
