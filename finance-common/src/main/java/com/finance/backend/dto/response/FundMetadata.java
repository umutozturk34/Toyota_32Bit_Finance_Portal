package com.finance.backend.dto.response;

import java.math.BigDecimal;

public record FundMetadata(
        String fundType,
        BigDecimal portfolioSize,
        BigDecimal investorCount,
        BigDecimal bulletinPrice,
        BigDecimal shareCount
) implements MarketAssetMetadata {
}
