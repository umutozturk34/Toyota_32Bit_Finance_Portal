package com.finance.backend.dto.response;

import java.math.BigDecimal;

public record CommodityMetadata(
        BigDecimal sellingPrice,
        BigDecimal currentPriceUsd,
        BigDecimal previousPriceUsd,
        String unit
) implements MarketAssetMetadata {
}
