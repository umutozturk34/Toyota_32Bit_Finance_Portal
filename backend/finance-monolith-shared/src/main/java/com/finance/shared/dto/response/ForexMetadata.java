package com.finance.shared.dto.response;

import java.math.BigDecimal;

public record ForexMetadata(
        BigDecimal buyingPrice,
        BigDecimal sellingPrice,
        BigDecimal effectiveBuyingPrice,
        BigDecimal effectiveSellingPrice,
        boolean tradable
) implements MarketAssetMetadata {
}
