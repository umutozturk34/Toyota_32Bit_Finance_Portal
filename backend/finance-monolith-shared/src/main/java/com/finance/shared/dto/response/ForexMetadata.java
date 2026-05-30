package com.finance.shared.dto.response;

import java.math.BigDecimal;

/** Forex-specific {@link MarketAssetMetadata}: bank vs effective buy/sell quotes and tradability flag. */
public record ForexMetadata(
        BigDecimal buyingPrice,
        BigDecimal sellingPrice,
        BigDecimal effectiveBuyingPrice,
        BigDecimal effectiveSellingPrice,
        boolean tradable
) implements MarketAssetMetadata {
}
